def truncate_docx(input_file, output_file, keep_percent=30):
    """
    Обрезает файл, оставляя только указанный процент данных
    """
    with open(input_file, 'rb') as f:
        data = f.read()
    
    # Оставляем только часть файла
    keep_size = int(len(data) * keep_percent / 100)
    corrupted_data = data[:keep_size]
    
    with open(output_file, 'wb') as f:
        f.write(corrupted_data)
    
    print(f"Файл обрезан до {keep_percent}% от оригинала: {output_file}")

truncate_docx("corrupted.docx", "corrupted2.docx", keep_percent=75)
