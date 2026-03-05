@echo off
python -m venv .venv
call .venv\Scripts\activate
python -m pip install --upgrade pip
python -m pip install numpy scipy pyinstaller
pyinstaller --onefile --name python_worker python_worker.py
echo Built: dist\python_worker.exe