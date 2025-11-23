package ru.grafit.recognition.common.storage;

import java.io.InputStream;

public interface StorageService {
    /**
     * Загружает аудио файл в хранилище
     * @param audioData данные аудио файла
     * @param taskId идентификатор задачи
     * @return URL/ключ файла в хранилище
     */
    String uploadAudio(byte[] audioData, String taskId);

    /**
     * Скачивает аудио файл из хранилища
     * @param fileKey ключ файла
     * @return данные аудио файла
     */
    byte[] downloadAudio(String fileKey);

    /**
     * Удаляет аудио файл из хранилища
     * @param fileKey ключ файла
     */
    void deleteAudio(String fileKey);

    /**
     * Проверяет существование файла
     * @param fileKey ключ файла
     * @return true если файл существует
     */
    boolean exists(String fileKey);
}

