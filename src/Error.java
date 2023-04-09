import java.util.*;

public class Error {
    static public Map<Integer, String> errors = new TreeMap<>();
    static public Map<Integer, String> infos = new TreeMap<>();
    static public ArrayList<ErrorItem> nonRepeatErrors = new ArrayList<>();

    static public void add(Integer line, String eid, String info) {
        //System.out.println(line+" "+eid);
        errors.put(line, eid);
        infos.put(line, info);
        nonRepeatErrors.add(new ErrorItem(line,eid,info));
    }

    static public void showErrors() {
        int i = 0;
        System.out.println("============Errors==============");
        for (Integer key : errors.keySet()) {
            System.out.println(key + ", " + errors.get(key) + ", " + infos.get(key));
        }
        System.out.println("============Errors-End==============");
    }

    static public void skip(Set<Integer> errorLinesPre){
        for(Integer line : errors.keySet()){
            if(!errorLinesPre.contains(line)){
                errors.remove(line);
                infos.remove(line);
            }
        }
        nonRepeatErrors.removeIf(item -> !errorLinesPre.contains(item.line));
    }

    static public String retErrors(){

        StringBuilder stringBuffer = new StringBuilder();
        for(Integer key :errors.keySet()){
            stringBuffer.append(key).append(" ").append(errors.get(key)).append("\n");
        }
        return stringBuffer.toString();

        /*
        StringBuilder stringBuffer = new StringBuilder();
        for(ErrorItem item:nonRepeatErrors){
            stringBuffer.append(item.line).append(" ").append(item.eid).append("\n");
        }
        return stringBuffer.toString();

         */
    }
}

class ErrorItem{
    int line;
    String eid;
    String info;

    public ErrorItem(int line, String eid, String info) {
        this.line = line;
        this.eid = eid;
        this.info = info;
    }

}