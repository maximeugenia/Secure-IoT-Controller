
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileReader;
import java.util.LinkedList;

public class Tester {

    public static void main(String args[]) {
        File configFile = new File("../config.json");
        JsonElement config = null;
        try {
            config = JsonParser.parseReader(new FileReader(configFile));
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        Gson gson = new Gson();
        ControllerState state = new ControllerState(config);
        //System.out.println(state.getSensors());
        Parser p = new Parser(state);
        LinkedList<String> input = new LinkedList<String>();

        String[] program1 = new String[] { "as principal admin password \"admin\" do ", "//coment",
                "set temperature = 100", "set smoke = 1", "return 0", "***", };

        String[] prog1 = new String[] { "as principal admin password \"admin\" do", "set door = 0", "exit", "***" };

        String[] prog2 = new String[] { "as principal admin password \"admin\" do",
                "create principal bob \"bobpass\"", "exit", "***" };

        String[] prog3 = new String[] { "as principal admin password \"admin\" do", "create principal bob \"bobpass\"",
                "change password bob \"bobpass1\"", "exit", "***" };

        String[] prog4 = new String[] { "as principal admin password \"admin\" do", "local x = 1",
                "exit", "***" };

        String[] prog5 = new String[] { "as principal admin password \"admin\" do","set x = 10", "if x = 10 then set x = 1",
                "exit", "***" };

        String[] prog6 = new String[] { "as principal admin password \"admin\" do", "set variable_name = 10", "create principal bob \"bobpass\"",
                "set delegation variable_name admin read -> bob", "exit", "***" };

        String[] prog7 = new String[] { "as principal admin password \"admin\" do",
                "set variable_name = 10",
                "create principal bob \"bobpass\"",
                "set delegation variable_name admin read -> bob",
                "delete delegation variable_name admin read -> bob", "exit", "***" };

        String[] prog8 = new String[] { "as principal admin password \"admin\" do", "default delegator = bob",
                "exit", "***" };

        String[] prog9 = new String[] { "as principal admin password \"admin\" do", "print variable_name", "exit",
                "***" };

        String[] prog10 = new String[] { "as principal admin password \"admin\" do",
                "set rule arule = if x = 1 then set x = 2", "exit", "***" };

        String[] prog11 = new String[] { "as principal admin password \"admin\" do", "activate rule arule", "exit",
                "***" };

        String[] prog12 = new String[] { "as principal admin password \"admin\" do", "deactivate rule arule", "exit",
                "***" };

        for (String line : prog7) {
            input.add(line);
        }

        LinkedList output = p.parse(input);
        for (Object current : output) {
            //System.out.println(gson.toJson(current));
        }

        // //System.out.println(p.parsePrimCmd("set x = 1"));
    }
}