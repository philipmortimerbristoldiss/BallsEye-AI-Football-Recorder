package com.dji.sdk.sample.demo.gimbal;

import android.os.Environment;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class FileWriterResults {

    //Writes results to text file in documents
    public static void writeToFile(String fileName, String content) {
        try {
            File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            File file = new File(documentsDir, fileName);
            FileOutputStream outputStream = new FileOutputStream(file);
            outputStream.write(content.getBytes());
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
