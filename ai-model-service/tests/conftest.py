"""
conftest.py
===========
Pytest configuration: adds the ai-model-service/src directory to sys.path
so that test files can import from data_generation without installing the package.
"""
import sys
from pathlib import Path

# ai-model-service/src
_SRC_DIR = Path(__file__).resolve().parent.parent / "src"
if str(_SRC_DIR) not in sys.path:
    sys.path.insert(0, str(_SRC_DIR))
