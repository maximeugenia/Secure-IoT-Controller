
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import outputs.*;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedList;

public class Parser {

    public LinkedList<Object> output;
    private Pattern comment = Pattern.compile("^//+ *[A-Za-z0-9_ ,;\\.?!-]* *$");
    private String principal;
    private String password;
    private String status = "";
    private String STATUSFAILED = "{\"status\":\"FAILED\"}\n";
    private LinkedList<Object> faild = new LinkedList<>();
    private ControllerState controller;
    private LinkedList statusOutput;
    private LinkedList failOutput;
    private Map<String, List<Integer>> local_variables;
    private boolean checkingRule;

    public Parser(ControllerState controller) {
        this.output = new LinkedList<>();
        this.principal = "";
        this.password = "";
        this.status = "";
        this.controller = controller;
        this.faild.add(STATUSFAILED);
        this.statusOutput = new LinkedList();
        this.failOutput = new LinkedList();
        this.failOutput.add(new Status("FAILED"));
        this.local_variables = new HashMap<String, List<Integer>>();
        checkingRule = false;
    }



    public LinkedList<Object> parse(LinkedList<String> list) {
        controller.saveState();
        if (list == null) {
            return failOutput;
        }

        String line = list.pollFirst();

        // Check if there are lines to work on
        if (line == null) {

            return failOutput;
        }
        // Check if this is a comment line
        Matcher matcher = comment.matcher(line);
        if (matcher.find()) {
            return parse(list); // basicaly go to the next line
        }

        // Check if the first line of the program defines the current principal
        Pattern pattern = Pattern.compile(
                "^ *as +principal +([A-Za-z][A-Za-z0-9_]*) +password +\"([A-Za-z0-9_ ,;\\.?!-]*)\" +do *(// *[A-Za-z0-9_ ,;\\.?!-]*)? *$");
        Matcher m = pattern.matcher(line);
        if (m.find()) {

            principal = m.group(1);
            password = m.group(2);

            if(password.length()>65535){
                return failOutput;
            }

            // We need to call some funcion to check to passward ....
            //System.out.println("Set the current principal and password here as well as create the controller state");
            if (controller.check_principle_and_password(principal, password) == false) {
                //System.out.println("The principal and password did not match");
                //System.out.println(principal + " " + password);
                return failOutput;
            }

        } else {
            return failOutput;
        }

        parseCmd(list);

        if(status.contains("FAIL")){
            controller.restoreState();
            //go back and rewind all changes that were make to the controllerstate
            return failOutput;
        }

        if(status.contains("DENIED")){
            Object lastStatus = output.getLast();
            LinkedList<Object> retValue = new LinkedList<Object>();
            retValue.add(lastStatus);
            return retValue;
        }

        List<RuleOutput> rule_output = controller.doRules();

        output.addAll(rule_output);

        return output;
    }

    // Parse the Comands
    private void parseCmd(LinkedList<String> list) {
        // takes the first line and removes it from the list
        String line = list.pollFirst();
        // check if this the are lines to be working on
        //System.out.print("parseCmd current string:");
        //System.out.println(line);

        if (line == null) {
            output.add(new Status("FAILED"));
            return;
        }
        // Check if this is a comment line
        Matcher m = comment.matcher(line);
        if (m.find()) {
            //System.out.println("this is a comment line moving onto the next");
            parseCmd(list); // basicaly go to the next line
            return;
        }

        Pattern p = Pattern.compile("^ *\\*\\*\\* *(// *[A-Za-z0-9_ ,;\\.?!-]*)? *$"); // end of the line
        Matcher endOfLine = p.matcher(line);
        if (endOfLine.find()) {
            //System.out.println("found the end of program [***]");
            status = "***"; // json
            // output.add(status);
            return;
        }

        // if we saw a return or exit before the current line in the program then it
        // should end with ***
        if (status.contains("exit") || status.contains("return")) {
            // found more lines after getting an exit or return
            output.add(new Status("FAILED"));
            return;
        }

        Pattern patExit = Pattern.compile("^ *exit *(// *[A-Za-z0-9_ ,;\\.?!-]*)? *$");
        Matcher matExit = patExit.matcher(line);
        if (matExit.find()) {
            //System.out.println("found the exit command in the program");
            // return status exit;
            status = "exit"; /// ==> json
            // output.add(status);
            parseCmd(list);
            return;
        }

        Pattern patRet = Pattern.compile("^ *return +(.*) *(// *[A-Za-z0-9_ ,;\\.?!-]*)? *$");
        Matcher matRet = patRet.matcher(line);
        if (matRet.find()) {
            //System.out.println("Found this line to be the return line");
            //System.out.println(matRet.group(1));
            String theExpr = parseExpr(matRet.group(1));
            // evaluate the expression and then add it to the output
            if(theExpr.contains("FAIL")){
                status = "FAIL";
                return;
            }
            if(theExpr.contains("SEC")){
                
                output.add(new Status("DENIED_READ"));
                status = "DENIED";
                //parseCmd(list);
                return;
            }
            else{
                output.add(new StatusOutput("RETURNING",theExpr));

                parseCmd(list);
                return;
            }
            //status = "return  " + theExpr; // => json

        }
        // Not comment, not endOfFile, not exit, not return, parse the primCmd
        //System.out.println("Moving into parsePrimCmd");
        String cmd = parsePrimCmd(line);
        if (cmd.equals(STATUSFAILED) || status.contains("FAIL")) { //// if any of the comands return fail...then send the faild status
            //output.add("faild");
            return;
        }
        else if(output.get(output.size() - 1).toString().contains("DENIED")){
                status = "DENIED";
                //System.out.println("BEFORE CUTTING THE OUTPUT: " + output);
               LinkedList<Object> deniedOutput = new LinkedList<Object>();
                deniedOutput.add(output.get(output.size() - 1));
                output = deniedOutput;
               return;
        } else {
            //output.add(cmd); /// otherwise add the command to the output
        }

        parseCmd(list);

    }

    public String parseExpr(String exp) {
        String expres = STATUSFAILED;
        Pattern p1 = Pattern.compile("^ *([A-Za-z][A-Za-z0-9_]*|-?[0-9]+|[A-Za-z][A-Za-z0-9\\_]*\\.[0-9]+) +[\\+|-|/|\\*] +([A-Za-z][A-Za-z0-9_]*|-?[0-9]+|[A-Za-z][A-Za-z0-9\\_]*\\.[0-9]+) *$");
        ////System.out.println(exp);
        Matcher m1 = p1.matcher(exp);
        if (m1.find()) {
            //System.out.println("found that the expression is a math op");
            ////System.out.println(m1.group(1));
            String leftValue = m1.group(1);
            String rightValue = m1.group(2);

            String parsedLeft = parseValue(leftValue);
            String parsedRight = parseValue(rightValue);

            //either a fail or a security problem
            if(parsedLeft.contains("FAIL") || parsedLeft.contains("SEC") || parsedRight.contains("FAIL") || parsedRight.contains("SEC")){
                if(parsedLeft.contains("FAIL") || parsedRight.contains("FAIL")){
                    return "FAIL_CONDITION_FAILURE";
                }
                if(parsedLeft.contains("SEC") || parsedRight.contains("SEC")){
                    return "SECURITY_VIOLATION_IN_EXPR";
                }
                return String.format("FAILED_EXPRESSION: LEFT: [%s] RIGHT: [%s]",parsedLeft, parsedRight);
            }

            if(exp.contains("+")){
                return String.valueOf(Integer.parseInt(parsedLeft) + Integer.parseInt(parsedRight));
            }
            else if(exp.contains("-")){
                return String.valueOf(Integer.parseInt(parsedLeft) - Integer.parseInt(parsedRight));
            }
            else if(exp.contains("/")){
                if(Integer.parseInt(parsedRight) == 0){
                    output.add(new Status("FAILED"));
                    return "FAILED_DIVIDE_BY_ZERO";
                }
                else{
                    return String.valueOf(Integer.parseInt(parsedLeft) / Integer.parseInt(parsedRight));
                }
            }
            //assume last condition is *
            else{
                return String.valueOf(Integer.parseInt(parsedLeft) * Integer.parseInt(parsedRight));
            }

            // String value1 = parseValue(m1.group(0));
            // if (!value1.equals(STATUSFAILED)) {
            //     String math_op = m1.group(1);
            //     if (!math_op.equals(STATUSFAILED)) {
            //         String value2 = parseValue(m1.group(2));
            //         if (!value2.equals(STATUSFAILED)) {
            //             // expres = EVALUATE VALUE1 MATH_OP VALUE2
            //         }
            //     }
            // }
            // if any of the value or operation doesn't fail, the the exprs ahould have a
            // correct value;
        }

        ////System.out.println(exp);
        Pattern p2 = Pattern.compile("^ *(mean|min|max|count) +([A-Za-z][A-Za-z0-9_]*) *$");
        Matcher m2 = p2.matcher(exp);
        if (m2.find()) {
            //System.out.println("found that the expression is a function with one variable");
            
            String fun = m2.group(1);
            String variable_name = m2.group(2);

            if(controller.getSensors().containsKey(variable_name) == false && controller.getOutputDevices().containsKey(variable_name) == false){
                output.add(new Status("FAILED"));
                return "FAIL_VARIABLE_NO_EXIST";
            }
            
            //List<Delegation> principal_delegations = controller.getDelegations().get("admin");
            List<Delegation> principal_delegations = controller.getDelegations().get(principal);
            boolean hasRight = false;
            for(Delegation current: principal_delegations){
                if(current.getSymbol().equals(variable_name) && current.hasRight(ControllerState.RIGHT.READ)){
                    hasRight = true;
                }
            }

            if(hasRight == false){
                output.add(new Status("DENIED_READ"));
                return "SEC_DENIED_READ";
            }

            List<Integer> valueList = controller.getValues(variable_name);

            if(fun.equals("mean")){
                int sum = 0;
                for(Integer value: valueList){
                    sum += value;
                }
                return String.valueOf(sum / valueList.size());
            }
            else if(fun.equals("max")){
                int max = Integer.MIN_VALUE;
                for(Integer value: valueList){
                    max = Math.max(max,value);
                }
                return String.valueOf(max);
            }
            else if(fun.equals("min")){
                int min = Integer.MAX_VALUE;
                for(Integer value: valueList){
                    min = Math.min(min,value);
                }
                return String.valueOf(min);
            }
            //assume function is count
            else{
                return String.valueOf(valueList.size());
            }
 
        }
        Pattern p3 = Pattern.compile("^ *([mean|min|max|count]+) +([A-Za-z][A-Za-z0-9_]*) +(-?[0-9]+) (-?[0-9]+) *$");
        Matcher m3 = p3.matcher(exp);
        if (m3.find()) {
            //System.out.println("Found that the expression is a function with 3 variables");
            String fun = m3.group(1);
            String variable_name = m3.group(2); // check to see if it is a variable??????
            String i = m3.group(3); // can be converted into an integer
            String j = m3.group(4); // can be converted into an integer
            // expres = call the funtion with its variable
            // //System.out.println(fun);
            // //System.out.println(variable_name);
            // //System.out.println(i);
            // //System.out.println(j);

            if(controller.getSensors().containsKey(variable_name) == false && controller.getOutputDevices().containsKey(variable_name) == false){
                output.add(new Status("FAILED"));
                return "FAIL_VARIABLE_NO_EXIST";
            }

            if(Integer.parseInt(i) < 0|| Integer.parseInt(j) < 0){
                output.add(new Status("FAILED"));
                return "FAIL_FUNCTION_INDECIES_NEGATIVE";
            }

            int listSize;

            if(controller.getSensors().containsKey(variable_name)){
                listSize = controller.getSensors().get(variable_name).size();
            }
            //assume it is in output_sensors
            else{
                listSize = controller.getOutputDevices().get(variable_name).size();
            }

            if(Integer.parseInt(i) > listSize || Integer.parseInt(j) > listSize){
                output.add(new Status("FAILED"));
                return "FAIL_FUNCTION_INDECIES_TOO_HIGH";
            }
            
            //List<Delegation> principal_delegations = controller.getDelegations().get("admin");
            List<Delegation> principal_delegations = controller.getDelegations().get(principal);
            boolean hasRight = false;
            for(Delegation current: principal_delegations){
                if(current.getSymbol().equals(variable_name) && current.hasRight(ControllerState.RIGHT.READ)){
                    hasRight = true;
                }
            }

            if(hasRight == false){
                output.add(new Status("DENIED_READ"));
                return "SEC_DENIED_READ";
            }
            else{
                List<Integer> valueList = controller.getValues(variable_name);
                List<Integer> flippedList = new LinkedList<Integer>();
                
                for(int counter = valueList.size() - 1; counter>=0; counter--){
                    flippedList.add(valueList.get(counter));
                }
                //System.out.println(flippedList);
                List<Integer> cutList = flippedList.subList(Integer.parseInt(i) - 1, Integer.parseInt(j));
                
                if(fun.equals("mean")){
                    int sum = 0;
                    for(Integer value: cutList){
                        sum += value;
                    }
                    return String.valueOf(sum / cutList.size());
                }
                else if(fun.equals("max")){
                    int max = Integer.MIN_VALUE;
                    for(Integer value: cutList){
                        max = Math.max(max,value);
                    }
                    return String.valueOf(max);
                }
                else if(fun.equals("min")){
                    int min = Integer.MAX_VALUE;
                    for(Integer value: cutList){
                        min = Math.min(min,value);
                    }
                    return String.valueOf(min);
                }
                //assume function is count
                else{
                    return String.valueOf(cutList.size());
                }
            }
        }

        // the exp is a <value>
        //System.out.println("Assuming expression is just <value>");
        //System.out.println(exp);
        expres = parseValue(exp);




        return expres;
    }

    // Parse Cond <cond>
    public String parseCond(String s) {
        String condition = STATUSFAILED;
        // <value> <cond_op> <value> //??????????? I need to show in the code of the
        // proffesor
        String conditionRegexold = "^ *(([A-Za-z][A-Za-z0-9_]*)|(-?[0-9]+)|([A-Za-z][A-Za-z0-9\\_]*\\.([0-9]+))) "
                + "+[=|>|<|>=|<=] +(([A-Za-z][A-Za-z0-9_]*)|(-?[0-9]+)|([A-Za-z][A-Za-z0-9_]*\\.([0-9]+))) *$";
        String conditionRegex = "^ *(.*) +([=|>|<|>=|<=]) +(.*)";
        Pattern pattern = Pattern.compile(conditionRegex);
        Matcher matcher = pattern.matcher(s);
        if (matcher.find()) {
            // //System.out.println(matcher.group(3));
            String leftValue = matcher.group(1);
            String cond_operator = matcher.group(2);
            String rightValue = matcher.group(3);

            //System.out.println(String.format("Found the condition as leftValue:[%s] conditional:[%s] rightValue:[%s]",
            //        leftValue, cond_operator, rightValue));

            String parsedLeftValue = parseValue(leftValue);
            String parsedRightValue = parseValue(rightValue);

            //System.out.println(String.format("Evalutated values as leftValue:[%s] rightValue:[%s]", parsedLeftValue,
            //        parsedRightValue));

            if(parsedLeftValue.contains("FAIL") || parsedLeftValue.contains("SEC") || parsedRightValue.contains("FAIL") || parsedRightValue.contains("SEC")){
                if(parsedLeftValue.contains("FAIL") || parsedRightValue.contains("FAIL")){
                    status = "FAIL";
                    //System.out.println(String.format("FAIL_COND_Evalutated values as leftValue:[%s] rightValue:[%s]", parsedLeftValue,parsedRightValue));
                    return "FAIL_EITHER_LEFT_OR_RIGHT_IS_FAIL";
                }
                if(parsedLeftValue.contains("SEC") || parsedRightValue.contains("SEC")){
                    //output.add(new Status("DENIED_READ"));
                    //System.out.println(String.format("FAIL_COND_Evalutated values as leftValue:[%s] rightValue:[%s]", parsedLeftValue,parsedRightValue));
                    return "SEC_DENIED_READ_IN_COND";
                }

                return String.format("FAIL_COND_Evalutated values as leftValue:[%s] rightValue:[%s]", parsedLeftValue,parsedRightValue);
            }

            boolean evaluation;

            if (cond_operator.equals("=")) {
                return String.valueOf(Integer.parseInt(parsedLeftValue) == Integer.parseInt(parsedRightValue));
            } else if (cond_operator.equals(">")) {
                return String.valueOf(Integer.parseInt(parsedLeftValue) > Integer.parseInt(parsedRightValue));
            } else if (cond_operator.equals("<")) {
                return String.valueOf(Integer.parseInt(parsedLeftValue) < Integer.parseInt(parsedRightValue));
            } else if (cond_operator.equals(">=")) {
                return String.valueOf(Integer.parseInt(parsedLeftValue) >= Integer.parseInt(parsedRightValue));
            } else if (cond_operator.equals("<=")) {
                return String.valueOf(Integer.parseInt(parsedLeftValue) <= Integer.parseInt(parsedRightValue));
            } else {
                return condition;
            }

        }
        return "EVAL_CONDITION";
    }

    // Parse the functions ///I doen't think I ever call this funtion, since I fail
    // if it doesn't match any of this
    // We can totaly delete this
    private String parseFunc(String fun) {

        if (fun.equals("mean") || fun.equals("min") || fun.equals("max") || fun.equals("count")) {
            return fun;
        }
        return STATUSFAILED; // "{\"status\":\"FAILED\"}\n"; //error ????????????
    }

    // return value is the evaluated value as a string or a failed of some kind
    public String parseValue(String s) {
        String value = "FAIL_STD_VALUE_PARSEVALUE";
        ////System.out.println(s);
        // *(// *[A-Za-z0-9_ ,;\\.?!-]*)? *$
        Pattern pattern = Pattern.compile("^ *([A-Za-z][A-Za-z0-9_]*|-?[0-9]+|[A-Za-z][A-Za-z0-9\\_]*\\.[0-9]+) *(?:// *[A-Za-z0-9_ ,;\\.?!-]*)? *$");
        Matcher matcher = pattern.matcher(s);
        if (matcher.find()) {
            //System.out.println("found a match for <value>: " + matcher.group(1));

            String capture = matcher.group(1);

            // capture is a variable name
            // evaluate the <value> and return that
            if (capture.matches("^[A-Za-z][A-Za-z0-9_]*$")) {
                //System.out.println("found the match as a variable name");
                if (local_variables.containsKey(capture)) {
                    List<Integer> variable_list = local_variables.get(capture);
                    return String.valueOf(variable_list.get(variable_list.size() - 1));
                } else {
                    //value = controller.getVariable("admin",capture);
                    value = controller.getVariable(principal, capture);
                    //System.out.println(value);
                    if (value.contains("FAIL")) {
                        if(checkingRule == false){
                            output.add(new Status("FAILED"));
                        }
                        
                    } else if (value.contains("SEC")) {
                        if(checkingRule == false){
                          output.add(new Status("DENIED_READ"));  
                        }
                        
                    } else {
                        // output.add(new Status("GOT_VALUE: " + value));
                    }
                }

            }
            // capture is an integer value
            else if (capture.matches("^-?[0-9]+$")) {
                //System.out.println("found the match as an integer: " + capture);
                value = String.valueOf(Integer.parseInt(capture));
            }
            // capture is a variable with a .
            else if (capture.matches("^[A-Za-z][A-Za-z0-9\\_]*\\.[0-9]+$")) {
                //System.out.println("found the match as a variable dot");
                int offset = Integer.valueOf(capture.substring(capture.indexOf(".") + 1));
                String variable_name = capture.substring(0, capture.indexOf("."));
                if(local_variables.containsKey(variable_name)){
                    List<Integer> valueList = local_variables.get(variable_name);
                    value = String.valueOf(valueList.get(valueList.size() - 1 - offset));
                }
                else{
                    //value = controller.getPastVariable("admin", variable_name, offset);
                    value = controller.getPastVariable(principal, variable_name, offset);
                    //System.out.println("VARIABLENAME: "+ variable_name +" GETTING A PAST VARIABLE FAILED: " + value);
                    if (value.contains("FAIL")) {
                        if(checkingRule == false){
                            output.add(new Status("FAILED"));
                        }
                        
                    } else if (value.contains("SEC")) {
                        if(checkingRule == false){
                            output.add(new Status("DENIED_READ"));
                        }
                        
                    } else {
                        // output.add(new Status("GOT_VALUE: " + value));
                    }
                }
                //value = "<value>variabledot";
            }
            // fail if none of them match
            else {
                if(checkingRule == false){
                    output.add(new Status("FAILED"));
                }
                
                return "FAIL_NO_MATCH_FOR_<VALUE>";

            }
            // value = call the function to validate s;
        }
        else{
            if(checkingRule == false){
                output.add(new Status("FAILED"));
            }
            
            return "FAIL_NO_MATCH_FOR_<VALUE>";
        }
        return value;
    }

    // Parse the Mathematical Operations I don't thisk I use this function wither
    private String parseMathOp(String s) {
        if (s.equals("+") || s.equals("-") || s.equals("/") || s.equals("*")) {
            return s;
        }
        return STATUSFAILED; // "{\"status\":\"FAILED\"}\n"; //error ????????????
    }

    // Parse Conditional Operations , I don't need this funtion either
    private String parseCondOp(String s) {
        if (s.equals("=") || s.equals(">") || s.equals("<") || s.equals(">=") || s.equals("<=")) {
            return s;
        } else {

            return STATUSFAILED; // "{\"status\":\"FAILED\"}\n"; //error ????????????
        }
    }

    public String parsePrimCmd(String cmd) {
        //System.out.println(String.format("parsePrimCmd: [%s]",cmd));
        String command = STATUSFAILED;
        String prin;
        String pass;

        Status retValue;

        Pattern p1 = Pattern.compile(
                "^ *create +principal +([A-Za-z][A-Za-z0-9_]*) +\"([A-Za-z0-9_ ,;\\.?!-]*)\" *(// *[A-Za-z0-9_ ,;\\.?!-]*)? *$");
        Matcher m1 = p1.matcher(cmd);
        if (m1.find()) {
            //System.out.println("Found the primitive command as create principal");
            // //System.out.println(m1.group(2));
            String principal_name = m1.group(1);
            String principal_password = m1.group(2);

            if(principal_name.equals("all")){
                status = "FAIL";
                return "TRIED TO MAKE A PRINCIPAL WITH NAME ALL";
            }

            // command = call the funtion to creat a principal;
            //retValue = controller.create_principle("admin", principal_name, principal_password);
            retValue = controller.create_principle(principal, principal_name, principal_password);
            //System.out.println("OUTPUT_FROM_CREATE_PRINCIPLE: " + retValue);
            if(retValue.getStatus().contains("FAILED")){
                
                status = "FAILED";    
            }
            else{
                
                output.add(retValue);
            }

            return "create principal";
        }

        Pattern p2 = Pattern.compile(
                "^ *change +password +([A-Za-z][A-Za-z0-9_]*) +\"([A-Za-z0-9_ ,;\\.?!-]*)\" *(// *[A-Za-z0-9_ ,;\\.?!-]*)? *$");
        Matcher m2 = p2.matcher(cmd);
        if (m2.find()) {
            //System.out.println("Found the primitive command as change password");
            ////System.out.println(m2.group(2));
            String principal_to_change = m2.group(1);
            String new_password = m2.group(2);
            if(new_password.length() > 65535){
                status = "FAILED";
                return "change password";
            }
            // command = call the function to change password p s
            //retValue = controller.change_password("admin", principal_to_change, new_password);
            retValue = controller.change_password(principal, principal_to_change, new_password);
            if(retValue.getStatus().contains("FAILED")){
                status = "FAILED";    
            }
            else{
                output.add(retValue);
            }

            return "change password";
        }

        Pattern p3 = Pattern.compile("^ *set +([A-Za-z][A-Za-z0-9_]*) += +(.+)+ *(// *[A-Za-z0-9_ ,;\\.?!-]*)? *$");
        Matcher m3 = p3.matcher(cmd);
        if (m3.find()) {
            //System.out.println("Found the primitive command as set");
            String x1 = m3.group(1);

            if(x1.equals("all")){
                status = "FAIL";
                return "TRIED TO USE ALL AS A VARIABLE NAME";
            }

            // //System.out.println(m3.group(0));
            String expr1 = parseExpr(m3.group(2));
            if (!expr1.contains("FAIL")) {
                if(expr1.contains("SEC")){
                
                    output.add(new Status("DENIED_READ"));
                    status = "DENIED";
                    //parseCmd(list);
                    return "DENIED";
                }
                // command = set x = expr;
                //System.out.println("going to set a new value for variable: " + x1);
                retValue = controller.set(principal, x1, Integer.parseInt(expr1));
                if(retValue.getStatus().contains("FAILED")){
                    status = "FAILED";    
                }
                else{
                    output.add(retValue);
                }
                command = "SET";
            }
            else{
                status = "FAIL";
            }
            return command;
        }

        Pattern p4 = Pattern.compile("^ *local +([A-Za-z][A-Za-z0-9_]*) += +(.+)+ *(// *[A-Za-z0-9_ ,;\\.?!-]*)? *$");
        Matcher m4 = p4.matcher(cmd);
        if (m4.find()) {
            //System.out.println("Found the primitive command as local set");
            ////System.out.println(m4.group(2));
            String x2 = m4.group(1);

            if(x2.equals("all")){
                status = "FAIL";
                return "TRIED TO SET A LOCAL WITH NAME ALL";
            }

            if(local_variables.containsKey(x2) || controller.getSensors().containsKey(x2) || controller.getOutputDevices().containsKey(x2) || controller.getRules().containsKey(x2)){
                status = "FAIL";
                
                return "FAIL_LOCAL_VARIABLE_NAME_TAKEN";
            }
            String expr2 = parseExpr(m4.group(2));
            if (!expr2.contains("FAIL")) {
                    //System.out.println("CREATING A NEW LOCAL VARIABLE");
                    if(expr2.contains("SEC")){
                
                        output.add(new Status("DENIED_READ"));
                        status = "DENIED";
                        //parseCmd(list);
                        return "DENIED";
                    }
                    LinkedList<Integer> toAdd = new LinkedList<Integer>();
                    toAdd.add(Integer.parseInt(expr2));
                    local_variables.put(x2,toAdd);
                    output.add(new Status("LOCAL"));
                command = "LOCAL_SET";
            }
            else{
                status = "FAIL";
            }
            return command;
        }

        Pattern p5 = Pattern.compile("^ *if +(.+) +then (.+)+ *(// *[A-Za-z0-9_ ,;\\.?!-]*)? *$");
        Matcher m5 = p5.matcher(cmd);
        if (m5.find()) {
            //System.out.println("Found the primitive command as a conditional");
            // //System.out.println(m5.group(2));
            // m5.group(1) is <cond>
            // m5.group(2) is <prim_cmd>
            String p = parseCond(m5.group(1));
            //System.out.println("Evaluating condition returned:" + p);
            // include conditional to check if parseCond() returned true
            
            if (Boolean.parseBoolean(p)) { 

                String s = parsePrimCmd(m5.group(2)); // anything else will be parse in primCMD
                if (status.contains("FAIL")) {
                    //System.out.println("Parsing <prim_cmd> after <cond> completed");
                    return "FAIL";
                }
            }
            else{
                output.add(new Status("COND_NOT_TAKEN"));
            }

            return "COMPLETE_CONDITIONAL";
        }


        Pattern p6 = Pattern.compile(
                "^ *set +delegation +([A-Za-z][A-Za-z0-9_]*) +([A-Za-z][A-Za-z0-9_]*) +(read|write|delegate|toggle) +-> +([A-Za-z][A-Za-z0-9_]*) *(// *[A-Za-z0-9_ ,;\\.?!-]*)? *$");
        Matcher m6 = p6.matcher(cmd);
        if (m6.find()) {
            //System.out.println("Found primitive command as set delegation");
            // //System.out.println(m6.group(3));
            // groups:
            // 1: target_name
            // 2: giving principal
            // 3: right
            // 4: to principal

            // if target_name is "all" then this is a special condition we need to deal with
            String target = m6.group(1); // tgt
            String delegator = m6.group(2); // q
            String right = m6.group(3);
            String delegatee = m6.group(4); // p
            ControllerState.RIGHT delegateRight;
            if(right.equals("read")){
                delegateRight = ControllerState.RIGHT.READ;
            }
            else if(right.equals("write")){
                delegateRight = ControllerState.RIGHT.WRITE;
            }
            else if(right.equals("delegate")){
                delegateRight = ControllerState.RIGHT.DELEGATE;
            }
            //assume it is toggle
            else{
                delegateRight = ControllerState.RIGHT.TOGGLE;
            }
            // command = call the function for delegation.....
            //retValue = controller.set_delegation("admin", target, delegator, delegateRight, delegatee);
            retValue = controller.set_delegation(principal, target, delegator, delegateRight, delegatee);
            if(retValue.getStatus().contains("FAILED")){
                status = "FAILED";    
            }
            else{
                output.add(retValue);
            }
            return "SET_DELEGATION";
        }

        Pattern p7 = Pattern.compile(
                "^ *delete +delegation +([A-Za-z][A-Za-z0-9_]*) +([A-Za-z][A-Za-z0-9_]*) +(read|write|delegate|toggle) +-> +([A-Za-z][A-Za-z0-9_]*) *(// *[A-Za-z0-9_ ,;\\.?!-]*)? *$");
        Matcher m7 = p7.matcher(cmd);
        if (m7.find()) {
            //System.out.println("Found pimitive command as delete delegation");
            // //System.out.println(m7.group(4));
            // groups:
            // 1: target_name
            // 2: giving principal
            // 3: right
            // 4: to principal
            String target = m7.group(1); // tgt
            String delegator = m7.group(2); // q
            String right = m7.group(3);
            String delegatee = m7.group(4); // p
            // commend = call the functio to delete a delegation
            ControllerState.RIGHT delegateRight;
            if(right.equals("read")){
                delegateRight = ControllerState.RIGHT.READ;
            }
            else if(right.equals("write")){
                delegateRight = ControllerState.RIGHT.WRITE;
            }
            else if(right.equals("delegate")){
                delegateRight = ControllerState.RIGHT.DELEGATE;
            }
            //assume it is toggle
            else{
                delegateRight = ControllerState.RIGHT.TOGGLE;
            }
            // command = call the function for delegation.....
            //retValue = controller.delete_delegation("admin", target, delegator, delegateRight, delegatee);
            //System.out.println("before");
            retValue = controller.delete_delegation(principal, target, delegator, delegateRight, delegatee);
            //System.out.println("after" + retValue);
            
            if(retValue.getStatus().contains("FAILED")){
                status = "FAILED";    
            }
            else if(retValue.getStatus().contains("NOCHANGE")){
                //do nothing if there is no change since the delegation didn't exist
            }
            else{
                output.add(retValue);
            }
            return "DELETE_DELEGATION";
        }

        Pattern p8 = Pattern
                .compile("^ *default +delegator += +([A-Za-z][A-Za-z0-9_]*) *(// *[A-Za-z0-9_ ,;\\.?!-]*)? *$");
        Matcher m8 = p8.matcher(cmd);
        if (m8.find()) {
            //System.out.println("Found primitive command as default delegator");
            // //System.out.println(m8.group(1));
            String delegator = m8.group(1); // p
            // command = call the default delegator
            //retValue = controller.set_default_delegator("admin", delegator);
            retValue = controller.set_default_delegator(principal, delegator);
            if(retValue.getStatus().contains("FAILED")){
                status = "FAILED";    
            }
            else{
                output.add(retValue);
            }
            return "DEFAULT_DELEGATOR";
        }


        Pattern p9 = Pattern.compile("^ *print +(.*) *(// *[A-Za-z0-9_ ,;\\.?!-]*)? *$");
        Matcher m9 = p9.matcher(cmd);
        if (m9.find()) {
            //System.out.println("Found primitive command as print");
            // //System.out.println(m9.group(1));
            String expr = parseExpr(m9.group(1));
            // command = call the function to print the expr
            if (expr.contains("FAIL")) {
                status = "FAIL";
                return "FAIL IN PRINT";
            }
            else if(expr.contains("SEC")){
                output.add(new Status("DENIED_READ"));
            }
            else{
                output.add(new StatusOutput("PRINTING",expr));
            }
            return command;
        }


        Pattern p10 = Pattern.compile(
                "^ *set +rule +([A-Za-z][A-Za-z0-9_]*) += +if +(.*) +then +(.*) *(// *[A-Za-z0-9_ ,;\\.?!-]*)? *$");
        Matcher m10 = p10.matcher(cmd);
        if (m10.find()) {
            //System.out.println("Found pimitive command as set rule");

            String rule_name = m10.group(1);
            String rule_condition = m10.group(2);
            String rule_command = m10.group(3);

            //System.out.println(
            //        String.format("Found set rule input as rule_name:[%s] rule_condition:[%s] rule_command:[%s]",
            //                rule_name, rule_condition, rule_command));

            // set the rule in the controller
            checkingRule = true;
            String cond = parseCond(rule_condition);
            checkingRule = false;
            if (!cond.contains("FAIL")) { /// && p it true ????? not sure what we need to return if the cond if
                                              /// false....
                //controller.saveState();
                //String primCond = parsePrimCmd(rule_command);
                //controller.restoreState();
                //output.remove(output.size() - 1);
                
                if(controller.parse_rule_command(rule_command)){
                    controller.set_rule(principal,rule_name,rule_condition,rule_command);
                    output.add(new Status("SET_RULE"));
                    return "SET_RULE";
                }
                else{
                    status = "FAIL";
                    return "FAILED TO MAKE NEW RULE";
                }
                /*
                if (!primCond.contains("FAIL")) {
                    // command = call the function to set the rull
                    //Status outputStatus = controller.set_rule("admin", rule_name, rule_condition, rule_command);
                    Status outputStatus = controller.set_rule(principal, rule_name, rule_condition, rule_command);
                    if (outputStatus.getStatus().equals("FAILED") == true) {
                        output.add(new Status("FAILED"));
                        status="FAIL";
                        return "FAILED_SET_RULE";
                    } else {
                        output.add(outputStatus);
                        return "SET_RULE";
                    }
                } else {
                    output.add(new Status("FAILED"));
                    status = "FAIL";
                }
                */
            } else {
                output.add(new Status("FAILED"));
                status = "FAIL";
            }

            return "SET_RULE";
        }


        Pattern p11 = Pattern.compile("^ *activate +rule +([A-Za-z][A-Za-z0-9_]*) *(// *[A-Za-z0-9_ ,;\\.?!-]*)? *$");
        Matcher m11 = p11.matcher(cmd);
        if (m11.find()) {
            //System.out.println("Found primitive command as activate rule");
            // //System.out.println(m11.group(1));
            String rule_name = m11.group(1);
            // command = call the funtion to activate the rule x
            //retValue = controller.activate_rule("admin", rule_name);
            retValue = controller.activate_rule(principal, rule_name);
            if(retValue.getStatus().contains("FAILED")){
                status = "FAILED";    
            }
            else{
                output.add(retValue);
            }
            return "ACTIVATE_RULE";
        }


        Pattern p12 = Pattern.compile("^ *deactivate +rule +([A-Za-z][A-Za-z0-9_]*) *(// *[A-Za-z0-9_ ,;\\.?!-]*)? *$");
        Matcher m12 = p12.matcher(cmd);
        if (m12.find()) {
            //System.out.println("Found primitive command as deactivate rule");
            String rule_name = m12.group(1);
            // command = call the funtion to deactivate the rule x
            //retValue = controller.deactive_rule("admin", rule_name);
            retValue = controller.deactive_rule(principal, rule_name);
            if(retValue.getStatus().contains("FAILED")){
                status = "FAILED";    
            }
            else{
                output.add(retValue);
            }
            return "DEACTIVATE_RULE";
        }

        // return status = STATUSFAILED; //"{\"status\":\"FAILED\"}\n";

        return command;
    }


    private String parseTarget(String s) {
        String target;
        Pattern p = Pattern.compile("^ *([A-Za-z][A-Za-z0-9_]*) *$");
        Matcher m = p.matcher(s);
        if (s.equals("all")) {
            return "all"; /// ?????
        } else if (m.find()) {
            String x = m.group(0);
            return s;
        }
        return status = STATUSFAILED; // "{\"status\":\"FAILED\"}\n";
    }


    private String parseRight(String s) {
        if (s.equals("read") || s.equals("write") || s.equals("delegate") || s.equals("toggle")) {
            return s;
        }
        return status = STATUSFAILED; // "{\"status\":\"FAILED\"}\n";
    }

    public static void main(String[] args) {
        LinkedList<String> program = new LinkedList<String>();
        Parser p = new Parser(null);

        String[] prog1 = new String[] { "as principal admin password \"password\" do", "set door = 0", "exit", "***" };

        String[] prog2 = new String[] { "as principal admin password \"password\" do",
                "create principal bob \"bobpass\"", "exit", "***" };

        String[] prog3 = new String[] { "as principal admin password \"password\" do",
                "change password bob \"bobpass\"", "exit", "***" };

        String[] prog4 = new String[] { "as principal admin password \"password\" do", "local x = variable_name",
                "exit", "***" };

        String[] prog5 = new String[] { "as principal admin password \"password\" do", "if x = 10 then set x = 1",
                "exit", "***" };

        String[] prog6 = new String[] { "as principal admin password \"password\" do",
                "set delegation variable_name admin read -> bob", "exit", "***" };

        String[] prog7 = new String[] { "as principal admin password \"password\" do",
                "delete delegation variable_name admin read -> bob", "exit", "***" };

        String[] prog8 = new String[] { "as principal admin password \"password\" do", "default delegator = bob",
                "exit", "***" };

        String[] prog9 = new String[] { "as principal admin password \"password\" do", "print variable_name", "exit",
                "***" };

        String[] prog10 = new String[] { "as principal admin password \"password\" do",
                "set rule arule = if x = 1 then set x = 2", "exit", "***" };

        String[] prog11 = new String[] { "as principal admin password \"password\" do", "activate rule arule", "exit",
                "***" };

        String[] prog12 = new String[] { "as principal admin password \"password\" do", "deactivate rule arule", "exit",
                "***" };

        for (String x : prog12) {
            program.add(x);
        }
        // LinkedList output = p.parse(program);
        // //System.out.println(output);

        String expr = "set rule x = if z = 1 then set y = 1";
        //System.out.println(p.parsePrimCmd(expr));

    }

    public void setPrinciple(String s){
        this.principal = s;
    }
}

