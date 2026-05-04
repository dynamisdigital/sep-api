#!/usr/bin/env bash
set -e

BASE_DIR="src/main/java/com/dynamis/sep_api"
MODULES=("identity" "usuarios" "onboarding" "credito" "contratos" "cobranca" "escrow" "backoffice" "financeiro" "credores" "pix" "shared")
LAYERS=("domain" "application" "infrastructure" "web")

for module in "${MODULES[@]}"; do
    for layer in "${LAYERS[@]}"; do
        DIR="$BASE_DIR/$module/$layer"
        mkdir -p "$DIR"
        echo "Pasta criada/garantida: $DIR"
    done
done

echo ""
echo "Estrutura criada. Cada combinacao modulo/layer deve ter seu package-info.java."
