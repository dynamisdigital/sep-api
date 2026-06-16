"""Testes automatizados gerados pelo Dynamis Control Center para Sep Api."""
import os
import sys

# Garante que o diretório raiz do projeto está no sys.path
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


class TestProjectStructure:
    """Verifica a estrutura básica do projeto."""

    def test_project_root_exists(self):
        """Verifica que o diretório raiz do projeto existe."""
        root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        assert os.path.isdir(root)

    def test_essential_files_exist(self):
        """Verifica que os arquivos essenciais do projeto existem."""
        root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        assert os.path.exists(os.path.join(root, "README.md")), "Arquivo README.md não encontrado"
