import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
public class Compiler {
    public static boolean isPolish = true;
    public static void main(String[] args){
        File file = new File("testfile.txt");
        String raw = readToString(file);
        GA out = new GA(raw);
        //writeToFile("error.txt",Error.retErrors());
        writeToFile("output.txt",PseudoCodes.printMidCode());
        writeToFile("mips.txt", PseudoCodes.printMips());
        //System.out.println(PseudoCodes.printMidCode());
        SymTable.writeTable();
        Error.showErrors();
    }

    public static String readToString(File file){
        Long filelength = file.length();     //获取文件长度
        byte[] filecontent = new byte[filelength.intValue()];
        try {
            FileInputStream in = new FileInputStream(file);
            in.read(filecontent);
            in.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new String(filecontent);//返回文件内容,默认编码
    }

    private static void writeToFile(String filename, String content) {
        File file = new File(filename);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }
        FileWriter fw = null;
        try {
            fw = new FileWriter(file);
            fw.write(content);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                assert fw != null;
                fw.close();
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
        }
    }
}
