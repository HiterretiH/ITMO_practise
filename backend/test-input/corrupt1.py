import zipfile
import os

def corrupt_docx(input_file, output_file):
    """
    Повреждает docx файл путём удаления важных XML файлов
    """
    # Временно распаковываем
    temp_dir = "temp_docx"
    with zipfile.ZipFile(input_file, 'r') as zip_ref:
        zip_ref.extractall(temp_dir)
    
    # Удаляем критически важный файл (содержит содержимое документа)
    content_file = os.path.join(temp_dir, "word", "document.xml")
    if os.path.exists(content_file):
        os.remove(content_file)  # Удаляем - документ становится пустым и повреждённым
    
    # Создаём повреждённый docx
    with zipfile.ZipFile(output_file, 'w', zipfile.ZIP_DEFLATED) as new_zip:
        for foldername, subfolders, filenames in os.walk(temp_dir):
            for filename in filenames:
                file_path = os.path.join(foldername, filename)
                arcname = os.path.relpath(file_path, temp_dir)
                new_zip.write(file_path, arcname)
    
    # Чистим временные файлы
    import shutil
    shutil.rmtree(temp_dir)
    
    print(f"Создан повреждённый файл: {output_file}")

# Использование
corrupt_docx("corrupted.docx", "corrupted1.docx")
