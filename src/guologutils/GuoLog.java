package guologutils;

import java.io.IOException;
import java.io.RandomAccessFile;

public class GuoLog {
    private static String logFilePath = "C:\\Users\\Administrator\\Desktop\\theOneLog";
    public static void log(String content) {
        try {
            // ��һ����������ļ���������д��ʽ
            RandomAccessFile randomFile = new RandomAccessFile(logFilePath, "rw");
            // �ļ����ȣ��ֽ���
            long fileLength = randomFile.length();
            // ��д�ļ�ָ���Ƶ��ļ�β��
            randomFile.seek(fileLength);
            randomFile.writeBytes(content+"\r\n");
            randomFile.close();
        } catch (IOException e) {
                e.printStackTrace();
        }
    }
}
