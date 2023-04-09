import java.util.HashMap;
import java.util.Map;

public class Memory {
    public static final int DATA_BASE = 0x10010000;
    public static final int STACK_BASE = 0x7ffffffc;
    public static int dataTop = DATA_BASE;

    public static int dataPointer = dataTop;
    public static Map<String, Integer> globalAddr = new HashMap<>();
    public static Map<String, Integer> tmpAddr = new HashMap<>();

    public static int addGlobal(String name, int size){
        int addr = dataPointer;
        dataPointer += size;
        globalAddr.put(name, addr);
        return addr+size;
    }

    public static int addTmp(String cur_func, int size){
        Integer oldPoint = tmpAddr.getOrDefault(cur_func, -4);
        tmpAddr.put(cur_func, oldPoint + size);
        return -(oldPoint+size);
    }

    public static int popTmp(String func, int size){
        Integer oldSpace = tmpAddr.getOrDefault(func, null);
        if (oldSpace != null && oldSpace >= size) {
            tmpAddr.put(func, oldSpace - size);
            return -(oldSpace-size);
        }
        return 0;
    }

}
