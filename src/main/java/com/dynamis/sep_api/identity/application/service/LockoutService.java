package com.dynamis.sep_api.identity.application.service;

import com.dynamis.sep_api.identity.application.exception.ContaBloqueadaException;
import com.dynamis.sep_api.identity.domain.model.LoginAttemptStatus;
import com.dynamis.sep_api.identity.domain.model.PoliticaLockout;
import com.dynamis.sep_api.identity.infrastructure.persistence.LoginAttemptRepository;
import com.dynamis.sep_api.identity.infrastructure.security.LockoutProperties;
import com.dynamis.sep_api.shared.audit.AuditLogSeguranca;
import com.dynamis.sep_api.shared.audit.AuditLogSegurancaRepository;
import com.dynamis.sep_api.shared.audit.TipoEventoSeguranca;
import com.dynamis.sep_api.shared.email.EmailService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Politica de account lockout (Sprint 5 Task 5.4; conformidade na Sprint 33).
 *
 * <p>Apos {@link LockoutProperties#getMaxAttempts()} (default 5) tentativas falhas dentro de {@link
 * LockoutProperties#getWindowMinutes()} (default 15 min), a conta fica bloqueada por {@link
 * LockoutProperties#getLockoutMinutes()} (default 30 min) contados a partir da falha mais recente
 * que fecha uma janela.
 *
 * <p>A decisao vive em {@link PoliticaLockout}; aqui fica so a leitura do historico. Ate a Sprint 33
 * o bloqueio era aproximado por contagem na janela de 30 min, o que bloqueava 5 falhas espalhadas
 * por 25 minutos e fazia o desbloqueio depender do envelhecimento das falhas, nao do bloqueio.
 */
@Service
public class LockoutService {

    private static final Logger log = LoggerFactory.getLogger(LockoutService.class);

    /**
     * Statuses que contam como falha. {@code CONTA_BLOQUEADA} fica de fora: desde a Sprint 34 os use
     * cases <b>registram</b> a tentativa barrada, e conta-la faria cada tentativa durante o bloqueio
     * renovar o proprio bloqueio — bloqueio auto-perpetuante.
     */
    private static final List<LoginAttemptStatus> STATUSES_FALHA =
            List.of(LoginAttemptStatus.SENHA_INVALIDA, LoginAttemptStatus.TOTP_INVALIDO);

    private final LoginAttemptRepository attemptRepository;
    private final AuditLogSegurancaRepository auditRepository;
    private final LockoutProperties properties;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    /**
     * Relogio injetado (Sprint 35 Task 35.6). A Sprint 33 extraiu {@link PoliticaLockout} como value
     * object puro justamente para decidir bloqueio sem relogio nem banco; este campo fecha o outro
     * lado, e o service deixa de ser a peca que so da para testar em tempo real.
     *
     * <p>Vem do {@code ClockConfig} do repo — {@code America/Sao_Paulo}, e nao UTC. O fuso nao muda o
     * resultado, porque toda comparacao e entre instantes.
     *
     * <p><b>Cobre o lado da leitura, e so ele.</b> Quem <b>grava</b> o historico que este service le
     * — {@code LoginAttempt.registrar} e {@code AuditLogSeguranca.de} — ainda carimba
     * {@code OffsetDateTime.now()} do sistema. Os dois relogios so concordam porque ambos sao o do
     * sistema, e por isso um IT de expiracao continua fora de alcance: sobrescrever este bean moveria
     * o leitor sem mover o escritor, e o historico nasceria no futuro. Unificar a escrita e follow-up
     * proprio (1 call site em {@code LoginAttempt}, 6 em {@code AuditLogSeguranca}).
     */
    private final Clock clock;

    public LockoutService(
            LoginAttemptRepository attemptRepository,
            AuditLogSegurancaRepository auditRepository,
            LockoutProperties properties,
            EmailService emailService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.attemptRepository = attemptRepository;
        this.auditRepository = auditRepository;
        this.properties = properties;
        this.emailService = emailService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * Falha se a conta estiver atualmente bloqueada.
     *
     * <p>{@code noRollbackFor}: como participante da transacao do chamador, lancar aqui marcaria a
     * transacao externa como rollback-only. Desde a Sprint 34 o chamador <b>continua trabalhando</b>
     * apos capturar (resolve o usuario para registrar a tentativa barrada), e uma transacao ja
     * condenada e uma armadilha — basta alguem passar a engolir a excecao, ou envolver o login numa
     * transacao maior que commite, para virar {@code UnexpectedRollbackException}. A decisao nao
     * muda: o chamador repropaga e o rollback acontece na fronteira dele.
     */
    @Transactional(readOnly = true, noRollbackFor = ContaBloqueadaException.class)
    public void verificar(String username) {
        Optional<Duration> restante = tempoRestanteDeBloqueio(username);
        if (restante.isPresent()) {
            throw new ContaBloqueadaException(restante.get());
        }
    }

    /**
     * Quanto falta do bloqueio vigente, ou vazio se a conta esta liberada.
     *
     * <p>Da Sprint 33 ate a Task 34.3 este metodo era {@code estaBloqueada} e devolvia
     * {@code boolean}, descartando o instante que {@link PoliticaLockout} ja calculava — por isso o
     * {@code 423} so sabia anunciar a duracao configurada. Quem decide continua sendo o dominio;
     * aqui so se le o historico.
     */
    private Optional<Duration> tempoRestanteDeBloqueio(String username) {
        OffsetDateTime agora = OffsetDateTime.now(clock);
        PoliticaLockout politica = politica();
        return politica.tempoRestanteDeBloqueio(falhasRecentes(username, agora, politica), agora);
    }

    private List<OffsetDateTime> falhasRecentes(String username, OffsetDateTime agora, PoliticaLockout politica) {
        OffsetDateTime inicioDaLeitura = agora.minus(politica.janelaDeLeitura());
        return attemptRepository.buscarInstantesDeFalha(
                username, STATUSES_FALHA, inicioDaLeitura, PageRequest.of(0, politica.limiteDeLeitura()));
    }

    /**
     * A politica vigente, montada da configuracao. Publica desde a Task 34.5 para que
     * {@code GET /auth/politica-lockout} anuncie <b>a mesma</b> politica que este servico aplica: se
     * o endpoint derivasse da configuracao por conta propria, o dia em que a fonte da verdade mudar
     * (parametro governado, politica por tenant, clamp de validacao) o anuncio continuaria correto
     * na aparencia e errado no conteudo.
     */
    public PoliticaLockout politica() {
        return new PoliticaLockout(
                properties.getMaxAttempts(),
                Duration.ofMinutes(properties.getWindowMinutes()),
                Duration.ofMinutes(properties.getLockoutMinutes()));
    }

    /**
     * Avalia se uma falha recem-registrada acabou de bloquear a conta e, em caso afirmativo, emite
     * email + audit log de LOCKOUT. Deve ser chamado pelo {@code AutenticarUsuarioUseCase} apos
     * persistir a tentativa falha.
     *
     * <p>A emissao e por <b>transicao</b>: so dispara quando o evento de bloqueio e a falha mais
     * recente, ou seja, a que acabou de ser registrada. Ate a Sprint 33 a condicao era
     * {@code falhasJanela == maxAttempts}, entao duas falhas concorrentes que levassem o contador de
     * 4 para 6 pulavam a igualdade e o bloqueio ficava <b>sem registro nenhum</b>.
     *
     * <p>Sob falhas concorrentes a notificacao pode sair mais de uma vez para o mesmo bloqueio —
     * trade-off deliberado: duplicar um aviso de seguranca e melhor que perde-lo.
     *
     * <p>Roda em transacao propria pelo mesmo motivo de {@code RegistrarTentativaLoginUseCase}: o
     * chamador lanca {@code BadCredentialsException} logo depois e o audit de LOCKOUT seria
     * desfeito junto.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void avaliarPosFalha(UUID usuarioId, String username) {
        OffsetDateTime agora = OffsetDateTime.now(clock);
        PoliticaLockout politica = politica();
        List<OffsetDateTime> falhas = falhasRecentes(username, agora, politica);
        Optional<OffsetDateTime> evento = politica.eventoDeBloqueio(falhas, agora);
        if (evento.isPresent() && evento.get().equals(falhas.get(0))) {
            log.atWarn()
                    .addKeyValue("event", "account_lockout")
                    .addKeyValue("durationMinutes", properties.getLockoutMinutes())
                    .log("Conta entrou em lockout");
            auditRepository.save(AuditLogSeguranca.registrar(
                    TipoEventoSeguranca.LOCKOUT, usuarioId, null, null, detalhesDoBloqueio(username)));
            emailService.enviar(
                    username,
                    "Conta SEP bloqueada temporariamente",
                    "Detectamos varias tentativas de login. Sua conta esta bloqueada por "
                            + properties.getLockoutMinutes() + " minutos.");
        }
    }

    /**
     * O {@code username} vem do corpo da request e a coluna e {@code jsonb}: montar o documento por
     * concatenacao deixava um username com aspas produzir JSON invalido, que o Postgres rejeita na
     * conversao — derrubando a gravacao inteira do rastro (Sprint 34 Task 34.2). O {@code @Email} do
     * DTO nao protege: a RFC admite local-part entre aspas.
     */
    private String detalhesDoBloqueio(String username) {
        Map<String, Object> detalhes = new LinkedHashMap<>();
        detalhes.put("username", username);
        detalhes.put("lockoutMinutes", properties.getLockoutMinutes());
        try {
            return objectMapper.writeValueAsString(detalhes);
        } catch (JsonProcessingException ex) {
            log.warn("falha ao serializar detalhes de auditoria de lockout", ex);
            return null;
        }
    }
}
