
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Set;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import outputs.*;

public class ControllerState {

    public static enum RIGHT {
        READ("READ"), WRITE("WRITE"), DELEGATE("DELEGATE"), TOGGLE("TOGGLE");

        private final String name;

        RIGHT(String name) {
            this.name = name;
        }

        public String getName() {
            return this.name;
        }

        public String toString() {
            return this.name;
        }
    }

    private Map<String, List<Integer>> sensors;
    private Map<String, List<Integer>> output_devices;

    // need a map of variables to be stored by the controller, instead of shoving
    // them into output_devices
    // set x = <value>
    // private Map<String, Integer> variables;

    // Principle map from username -> pasword
    private Map<String, String> principles;

    // delegation
    // when a q is given a delegation by p then whenever p has that delegation then
    // q does as well
    // map: name of principle -> list of delegations which has the variable it is
    // for and who it was from
    Map<String, List<Delegation>> delegations;

    // rules
    // map: name of rule -> Rule object
    Map<String, Rule> rules;

    // the principle with the rights
    String defaultDelegator;

    private Map<String, List<Integer>> sensors_SAVE;
    private Map<String, List<Integer>> output_devices_SAVE;
    private Map<String, String> principles_SAVE;
    private Map<String, List<Delegation>> delegations_SAVE;
    private Map<String, Rule> rules_SAVE;
    private String defaultDelegator_SAVE;

    public void saveState() {
        sensors_SAVE = new HashMap<String, List<Integer>>();
        for (Entry<String, List<Integer>> entry : sensors.entrySet()) {
            LinkedList<Integer> toAdd = new LinkedList<Integer>();
            for (Integer entry2 : entry.getValue()) {
                toAdd.add(entry2);
            }
            sensors_SAVE.put(entry.getKey(), toAdd);
        }
        output_devices_SAVE = new HashMap<String, List<Integer>>();
        for (Entry<String, List<Integer>> entry : output_devices.entrySet()) {
            LinkedList<Integer> toAdd = new LinkedList<Integer>();
            for (Integer entry2 : entry.getValue()) {
                toAdd.add(entry2);
            }
            output_devices_SAVE.put(entry.getKey(), toAdd);
        }
        principles_SAVE = new HashMap<String, String>();
        for (Entry<String, String> entry : principles.entrySet()) {
            principles_SAVE.put(entry.getKey(), entry.getValue());
        }
        delegations_SAVE = new HashMap<String, List<Delegation>>();
        for (Entry<String, List<Delegation>> entry : delegations.entrySet()) {
            LinkedList<Delegation> toAdd = new LinkedList<Delegation>();
            for (Delegation entry2 : entry.getValue()) {
                toAdd.add(entry2.copy());
            }
            delegations_SAVE.put(entry.getKey(), toAdd);
        }
        rules_SAVE = new HashMap<String, Rule>();
        for (Entry<String, Rule> entry : rules.entrySet()) {
            rules_SAVE.put(entry.getKey(), entry.getValue());
        }

        defaultDelegator_SAVE = defaultDelegator;
    }

    public void restoreState() {
        sensors = sensors_SAVE;
        output_devices = output_devices_SAVE;
        principles = principles_SAVE;
        delegations = delegations_SAVE;
        rules = rules_SAVE;
        defaultDelegator = defaultDelegator_SAVE;
    }

    private static Map<String, List<Integer>> extractMembers(JsonObject obj) {
        Set<Entry<String, JsonElement>> memberSet = obj.entrySet();
        Map<String, List<Integer>> retValue = new HashMap<String, List<Integer>>();
        for (Entry<String, JsonElement> pair : memberSet) {
            List<Integer> tempHolder = new LinkedList<Integer>();
            tempHolder.add(Integer.parseInt(pair.getValue().getAsString()));
            retValue.put(pair.getKey(), tempHolder);
        }
        return retValue;
    }

    public ControllerState(JsonElement parsedConfig, String adminPass, String hubPass) {
        JsonObject highestObj = parsedConfig.getAsJsonObject();
        sensors = extractMembers(highestObj.getAsJsonObject("sensors"));
        output_devices = extractMembers(highestObj.getAsJsonObject("output_devices"));
        principles = new HashMap<String, String>();
        principles.put("admin", adminPass);
        principles.put("hub", hubPass);
        principles.put("anyone", "");
        delegations = new HashMap<String, List<Delegation>>();
        delegations.put("anyone", new LinkedList<Delegation>());
        // set admin to have all delegations
        LinkedList<Delegation> adminDelegations = new LinkedList<Delegation>();
        for (String output_device : output_devices.keySet()) {
            adminDelegations.add(new Delegation(Arrays.asList(RIGHT.values()), output_device, "admin"));
        }
        for (String sensor : sensors.keySet()) {
            adminDelegations.add(new Delegation(RIGHT.READ, sensor, "admin"));
        }
        delegations.put("admin", adminDelegations);
        // set hub to have all delegations for sensors
        LinkedList<Delegation> hubDelegation = new LinkedList<Delegation>();
        for (String sensor : sensors.keySet()) {
            hubDelegation.add(new Delegation(RIGHT.WRITE, sensor, "admin"));
        }
        delegations.put("hub", hubDelegation);
        defaultDelegator = "anyone";
        rules = new HashMap<String, Rule>();
    }

    public ControllerState(JsonElement parsedConfig, String adminPass) {
        this(parsedConfig, adminPass, "hub");
    }

    public ControllerState(JsonElement parsedConfig) {
        this(parsedConfig, "admin", "hub");
    }

    public Map<String, List<Integer>> getSensors() {
        return this.sensors;
    }

    public Map<String, List<Integer>> getOutputDevices() {
        return this.output_devices;
    }

    public Map<String, String> getPrinciples() {
        return this.principles;
    }

    public Map<String, List<Delegation>> getDelegations() {
        return this.delegations;
    }

    // create a new principle
    // if the principle of the program is not the admin then return DENIED_WRITE
    // if the principle exists then return FAILED
    // add the principle and then return CREATE_PRINCIPLE
    public Status create_principle(String by_principle, String principle_name, String principle_password) {
        if (principles.containsKey(principle_name) == true) {
            return new Status("FAILED");
        }
        if (by_principle.equals("admin") == false) {
            return new Status("DENIED_WRITE");
        }
        principles.put(principle_name, principle_password);

        // give the new principle delegations that were given to the anyone principle or
        // the default delegator
        List<Delegation> anyoneDelegations = delegations.get("anyone");
        List<Delegation> defaultDelegations = delegations.get(defaultDelegator);

        List<Delegation> newDelegations = new LinkedList<Delegation>();
        newDelegations.addAll(anyoneDelegations);
        newDelegations.addAll(defaultDelegations);
        delegations.put(principle_name, newDelegations);

        return new Status("CREATE_PRINCIPLE");
    }

    // change the password of a current principle
    // if the principle doesn't exist then return failed
    // if the current principle doesn't match the principle to change or admin then
    // return DENIED_WRITE
    // change the value of principle key and return CHANGE_PASSWORD
    public Status change_password(String by_principle, String principle_name, String new_password) {
        if (principles.containsKey(principle_name) == false) {
            return new Status("FAILED");
        }
        if (by_principle.equals(principle_name) == false && by_principle.equals("admin") == false) {
            return new Status("DENIED_WRITE");
        }
        principles.put(principle_name, new_password);
        return new Status("CHANGE_PASSWORD");
    }

    private Delegation getDelegationForSymbol(String symbol, List<Delegation> principle_delegations) {
        Delegation retValue = null;
        for (Delegation currentVal : principle_delegations) {
            if (currentVal.getSymbol().equals(symbol)) {
                retValue = currentVal;
            }
        }
        return retValue;
    }

    private boolean checkDelegationChain(String variable, RIGHT right, String principal){
        //System.out.println(String.format("CHECK DELEGATION CHAIN [%s] [%s] [%s]", variable, right, principal));
        if(principal.equals("admin")){
            return true;
        }
        if(principal == null){
            return false;
        }
        List<Delegation> principal_delegations = delegations.get(principal);
        if(principal_delegations != null){
            boolean hasRight = false;
            String byWayOf = null;
            Delegation holder = getDelegationForSymbol(variable, principal_delegations);

            if(holder == null){
                return false;
            }
            else{
                hasRight = holder.hasRight(right);
                byWayOf = holder.getFrom();
            }

            
            
            return hasRight && checkDelegationChain(variable, right, byWayOf);
        }
        else{
            return false;
        }
    }

    // set the delegation of a right for a sensor or output device
    // by_principle doesn't have to the same as by
    // If tgt, by, or to doesn't exist then return FAILED
    // If by_principle is not admin or the same as by then return DENIED_WRITE
    // If by doesn't have delegate rights on tgt then return DENIED_WRITE
    // If all conditions are satisfied then return SET_DELEGATION
    public Status set_delegation(String by_principle, String tgt, String by, RIGHT right, String to) {
        if (sensors.containsKey(tgt) == false && output_devices.containsKey(tgt) == false
                && rules.containsKey(tgt) == false) {

            return new Status("FAILED1");
        }
        // //System.out.println(principles.containsKey(by_principle));
        // //System.out.println(principles.containsKey(by));
        // //System.out.println(principles.containsKey(to));
        if (principles.containsKey(by_principle) == false || principles.containsKey(by) == false
                || principles.containsKey(to) == false) {
            ////System.out.println("in here");
            return new Status("FAILED2");
        }
        ////System.out.println(by_principle.equals("admin"));
        ////System.out.println(by_principle.equals(by));
        if (by_principle.equals("admin") == false && by_principle.equals(by) == false) {
            // //System.out.println("in here");
            return new Status("DENIED_WRITE");
        }

        // if target is all then we need to do a special command
        if (tgt.equals("all")) {
            List<Delegation> bys_delgations = delegations.get(by);
            List<String> delegatableSymbols = new LinkedList<String>();

            // find the delegatable symbols
            for (Delegation current : bys_delgations) {
                if (current.hasRight(RIGHT.DELEGATE)) {
                    delegatableSymbols.add(current.getSymbol());
                }
            }

            // for each delegatable symbol add the right to to's delegation
            List<Delegation> principal_delegations = delegations.get(to);
            for (String currentSym : delegatableSymbols) {
                Delegation currentDelegation = getDelegationForSymbol(currentSym, principal_delegations);
                if (currentDelegation == null) {
                    principal_delegations.add(new Delegation(right, currentSym, by));
                } else {
                    currentDelegation.addRight(right);
                }
            }

            return new Status("SET_DELEGATION");

        } else {
            // check that by has the right to delegate permission for tgt
            boolean hasRight = false;
            if (by_principle.equals("admin")) {
                hasRight = true;
            } else {
                
                hasRight = checkDelegationChain(tgt, right, by);
            }

            if (hasRight == false) {
                return new Status("FAILED");
            }
 
            // if to already has delegate rights for tgt then don't do anything
            // else give to the delegate permission for tgt

            List<Delegation> principle_delegations = delegations.get(to);

            Delegation symbolDelegations = getDelegationForSymbol(tgt, principle_delegations);

            // if the principle already has the specific right for tgt then add delegate if
            // needed
            // else create a new delegation for symbol tgt, add delegate, and add that to
            // list of delegations of prinicple
            if (symbolDelegations == null) {
                principle_delegations.add(new Delegation(right, tgt, by));
            } else {
                ////System.out.println(String.format("current [%s] by [%s] to [%s]",by_principle,by,to));
                ////System.out.println(symbolDelegations);
                symbolDelegations.addRight(right);
            }

        }

        return new Status("SET_DELEGATION");
    }

    // delete a right delegation for a sensor or output device
    // by_principle doesn't have to be the same as by
    // if by, to, or tgt doesn't exist then return FAILED
    // if by_principle is not admin, by, or to then return DENIED_WRITE
    // If principle by doesn't have delegate permission for tgt then return
    // DENIED_WRITE
    // remove right from right list for symbol tgt in the list of delegations for to
    public Status delete_delegation(String by_principle, String tgt, String by, RIGHT right, String to) {
        if (sensors.containsKey(tgt) == false && output_devices.containsKey(tgt) == false) {
            return new Status("FAILED");
        }
        if (principles.containsKey(by) == false || principles.containsKey(to) == false) {
            return new Status("FAILED");
        }
        if (by_principle.equals("admin") == false && by_principle.equals(by) == false
                && by_principle.equals(to) == false) {
            return new Status("DENIED_WRITE");
        }

        if (tgt.equals("all")) {
            
            List<Delegation> bys_delgations = delegations.get(by);
            List<String> delegatableSymbols = new LinkedList<String>();

            // find the delegatable symbols
            for (Delegation current : bys_delgations) {
                if (current.hasRight(RIGHT.DELEGATE)) {
                    delegatableSymbols.add(current.getSymbol());
                }
            }

            // for each delegatable symbol add the right to to's delegation
            List<Delegation> principal_delegations = delegations.get(to);
            for (String currentSym : delegatableSymbols) {
                Delegation currentDelegation = getDelegationForSymbol(currentSym, principal_delegations);
                if (currentDelegation == null) {
                    principal_delegations.add(new Delegation(right, currentSym, by));
                } else {
                    currentDelegation.removeRight(right);
                }
            }

            return new Status("SET_DELEGATION");

        } else {
            boolean hasRight = false;

            //do not need to check that the current 
            for (Delegation current : delegations.get(by)) {
                if (current.getSymbol().equals(tgt)) {
                    hasRight = current.hasRight(RIGHT.DELEGATE);
                }
            }

            if (hasRight == false) {
                return new Status("DENIED_WRITE");
            }

            
            hasRight = checkDelegationChain(tgt, right, by);

            if(hasRight == false){
                return new Status("DENIED_WRITE");
            }
            

            ////System.out.println(String.format("[%s]",to));
            List<Delegation> principle_delegations = delegations.get(to);
            Delegation for_symbol = null;
            for (Delegation current : principle_delegations) {
                if (current.getSymbol().equals(tgt)) {
                    for_symbol = current;
                }
            }
            ////System.out.println(for_symbol);

            // to doesn't have any rights for this symbol
            if (for_symbol == null) {
                ////System.out.println("NO CHANGE DELEGATION");
                return new Status("NOCHANGE");
            } else {
                // to has some delegations for symbol tgt so we should remove delegate if they
                // have it
                for_symbol.removeRight(RIGHT.DELEGATE);
                return new Status("DELETE_DELEGATION");
            }
        }

    }

    // set the new default delegator which will be used to instantiate the rights of
    // a new principle
    // if newDefault doesn't exists as a principle then return FAILED
    // if the by_principle is not admin then return FAILED
    // on success return DEFAULT_DELEGATOR
    public Status set_default_delegator(String by_principle, String newDefault) {
        if (principles.containsKey(newDefault) == false) {
            return new Status("FAILED");
        }
        if (by_principle.equals("admin") == false) {
            return new Status("DENIED_WRITE");
        }
        defaultDelegator = newDefault;
        return new Status("DEFAULT_DELEGATOR");
    }

    //
    // public StatusOutput print(){}
    // this should be implemented in the paser since <expr> needs to be evaluated
    // and the output to be returned
    // can be found with this class's get methods

    // public Status local(){}
    // this should be implemented in the parser since the variable only exists in
    // the context of the current program being ran

    // set the state of a variable (output_device)
    // return FAILED if the name of the variable is the same name as a rule
    // if the output_device exists then the by_prinicple but has WRITE right on that
    // variable
    // if the output_device doesn't exist then create it and give the by_principle
    // all rights by admin
    public Status set(String by_principle, String variable_name, Integer new_value) {

        if (rules.containsKey(variable_name) == true) {
            return new Status("FAILED");
        }

        // device exists in output_devices
        if (output_devices.containsKey(variable_name)) {
            List<Delegation> delegations_for_principle = delegations.get(by_principle);
            boolean hasRight = false;
            // find the delegation for variable_name and if by_principle has the WRITE
            // permission
            /*
            for (Delegation current : delegations_for_principle) {
                if (current.getSymbol().equals(variable_name) == true && current.hasRight(RIGHT.WRITE)) {
                    hasRight = true;
                }
            }
            */
            
            hasRight = checkDelegationChain(variable_name, RIGHT.WRITE, by_principle);

            if (hasRight == true) {
                List<Integer> tempHolder = output_devices.get(variable_name);
                tempHolder.add(new_value);

            } else {
                return new Status("DENIED_WRITE");
            }
        } else {
            List<Integer> tempHolder = new LinkedList<Integer>();
            tempHolder.add(new_value);
            output_devices.put(variable_name, tempHolder);
            // give the current principle all the rights by admin
            set_delegation("admin", variable_name, "admin", RIGHT.READ, by_principle);
            set_delegation("admin", variable_name, "admin", RIGHT.WRITE, by_principle);
            set_delegation("admin", variable_name, "admin", RIGHT.DELEGATE, by_principle);
        }

        return new Status("SET");
    }

    private boolean checkRuleName(String rule_name) {
        return rules.containsKey(rule_name) == true && output_devices.containsKey(rule_name) == false;
    }

    private boolean checkRuleTogglePermission(String by_principle, String rule_name) {
        List<Delegation> principle_delegations = delegations.get(by_principle);
        boolean retValue = false;
        //System.out.println("PRINCIPAL DELEGATIONS: " + principle_delegations);
        for (Delegation current : principle_delegations) {
            if (current.getSymbol().equals(rule_name) == true) {
                retValue = current.hasRight(RIGHT.TOGGLE);
            }
        }

        return retValue;
    }

    public Status activate_rule(String by_principle, String rule_name) {

        if (checkRuleName(rule_name) == false) {
            return new Status("FAILED");
        } else {
            // check for toggle permissions
            //System.out.println("CHCECKING THE RULE PERMISSION RETURNED THIS: "
            //        + checkRuleTogglePermission(by_principle, rule_name));
            if (checkRuleTogglePermission(by_principle, rule_name) || by_principle.equals("admin")) {
                Rule currentRule = rules.get(rule_name);
                if (currentRule.isToggled() == false) {
                    currentRule.toggle();
                }

                return new Status("ACTIVATE_RULE");
            } else {
                return new Status("DENIED_WRITE");
            }
        }
    }

    public Status deactive_rule(String by_principle, String rule_name) {

        if (checkRuleName(rule_name) == false) {
            return new Status("FAILED");
        } else {
            // check for toggle permissions
            if (checkRuleTogglePermission(by_principle, rule_name) || by_principle.equals("admin")) {
                Rule currentRule = rules.get(rule_name);
                if (currentRule.isToggled() == true) {
                    currentRule.toggle();
                }
                return new Status("DEACTIVE_RULE");
            } else {
                //System.out.println(String.format("COULD NOT DEACTIVE RULE WITH PRINCIPAL [%s]",by_principle));
                return new Status("DENIED_WRITE");
            }
        }
    }

    // save a rule to be executed later
    // if the rule name is in output_devices, sensors, or a variable then return
    // FAILED
    // if the rule name does exist and the current user has WRITE permission then
    // update it and return SET_RULE
    public Status set_rule(String by_principle, String rule_name, String condition, String primitive_command) {
        if (sensors.containsKey(rule_name) || output_devices.containsKey(rule_name)) {
            return new Status("FAILED");
        }

        // if the rule exists then check for permission and update it
        if (rules.containsKey(rule_name)) {
            List<Delegation> principle_delegations = delegations.get(by_principle);
            boolean hasRight = false;
            for (Delegation current : principle_delegations) {
                if (current.getSymbol().equals(rule_name) && current.hasRight(RIGHT.WRITE)) {
                    hasRight = true;
                }
            }

            if (hasRight == false) {
                return new Status("DENIED_WRITE");
            } else {
                rules.put(rule_name, new Rule(rule_name, condition, primitive_command, by_principle));
                return new Status("SET_RULE");
            }
        }
        // if the rule does not exist then create it and set he current prinicple for
        // all conditions
        else {
            rules.put(rule_name, new Rule(rule_name, condition, primitive_command, by_principle));
            Status s1 = set_delegation("admin", rule_name, "admin", RIGHT.DELEGATE, by_principle);
            Status s2 = set_delegation("admin", rule_name, "admin", RIGHT.READ, by_principle);
            Status s3 = set_delegation("admin", rule_name, "admin", RIGHT.WRITE, by_principle);
            Status s4 = set_delegation("admin", rule_name, "admin", RIGHT.TOGGLE, by_principle);
            //System.out.println(String.format("s1: [%s] s2: [%s] s3: [%s] s4: [%s]", s1, s2, s3, s4));
            return new Status("SET_RULE");
        }
    }

    public boolean check_principle_and_password(String principle, String password) {
        return principles.containsKey(principle) && password.equals(principles.get(principle));
    }

    // if variable is a rule or doesn't exist then return a fail
    // if variable principle doesn't have read right for variable name then return
    // DENIEDREAD
    public String getVariable(String principle, String variable_name) {
        if (rules.containsKey(variable_name)) {
            return "FAIL_RULE_NAME";
        }

        if (sensors.containsKey(variable_name) == false && output_devices.containsKey(variable_name) == false) {
            return "FAIL_VARIABLE_NO_EXIST";
        }

        List<Delegation> principle_delegations = delegations.get(principle);
        boolean hasRead = false;
        //System.out.println(principle);
        //System.out.println(principles);
        /*
        for (Delegation current : principle_delegations) {
            //System.out.println(current.getSymbol());
            if (current.getSymbol().equals(variable_name) && current.hasRight(RIGHT.READ)) {
                hasRead = true;
            }
        }
        */
        hasRead = checkDelegationChain(variable_name, RIGHT.READ, principle);

        if (hasRead) {
            List<Integer> tempHolder;
            if (sensors.containsKey(variable_name)) {
                tempHolder = sensors.get(variable_name);
            } else {
                tempHolder = output_devices.get(variable_name);
            }

            return String.valueOf(tempHolder.get(tempHolder.size() - 1));
        } else {
            return "SEC_NO_READ";
        }
    }

    public String getPastVariable(String principle, String variable_name, int offset) {

        if (rules.containsKey(variable_name)) {
            return "FAIL_RULE_NAME";
        }

        if (sensors.containsKey(variable_name) == false && output_devices.containsKey(variable_name) == false) {
            // //System.out.println(sensors.keySet());
            // //System.out.println(variable_name);
            return "FAIL_VARIABLE_NO_EXIST";
        }

        List<Delegation> principle_delegations = delegations.get(principle);
        boolean hasRead = false;
        for (Delegation current : principle_delegations) {
            if (current.getSymbol().equals(variable_name) && current.hasRight(RIGHT.READ)) {
                hasRead = true;
            }
        }

        if (hasRead) {
            List<Integer> tempHolder;
            if (sensors.containsKey(variable_name)) {
                tempHolder = sensors.get(variable_name);
            } else {
                tempHolder = output_devices.get(variable_name);
            }

            if (offset > (tempHolder.size() - 1)) {
                return "FAIL_OFFSET_TOO_LARGE";
            }
            //System.out.println("GETTING PAST VARIABLE");
            //System.out.println(tempHolder);
            //System.out.println(offset);
            return String.valueOf(tempHolder.get(tempHolder.size() - 1 - offset));
        } else {
            return "SEC_NO_READ";
        }
    }

    public List<Integer> getValues(String variable_name) {
        if (sensors.containsKey(variable_name)) {
            return sensors.get(variable_name);
        }
        // assume it is in output_devices
        else {
            return output_devices.get(variable_name);
        }
    }

    public Map<String, Rule> getRules() {
        return rules;
    }

    private String parse_rule_value(String principal, String input) {
        Pattern pattern = Pattern.compile("^ *([A-Za-z][A-Za-z0-9_]*|-?[0-9]+|[A-Za-z][A-Za-z0-9\\_]*\\.[0-9]+) *$");
        Matcher matcher = pattern.matcher(input);

        if (matcher.find()) {
            String capture = matcher.group(1);
            // variable
            if (capture.matches("^[A-Za-z][A-Za-z0-9_]*$")) {
                String value = this.getVariable(principal, capture);
                if (value.contains("DENIED")) {
                    return "DENIED";
                } else {
                    return value;
                }
            }
            // integer
            else if (capture.matches("^-?[0-9]+$")) {
                return capture;
            }
            // past value
            else if (capture.matches("^[A-Za-z][A-Za-z0-9\\_]*\\.[0-9]+$")) {
                int offset = Integer.valueOf(capture.substring(capture.indexOf(".") + 1));
                String variable_name = capture.substring(0, capture.indexOf("."));
                String value = this.getPastVariable(principal, variable_name, offset);
                if (value.contains("DENIED")) {
                    return "DENIED";
                } else {
                    return value;
                }
            } else {
                return "FAIL";
            }
        } else {
            return "FAIL";
        }
    }

    public boolean parse_rule_command(String cmd) {
        Pattern p1 = Pattern.compile(
                "^ *create +principal +([A-Za-z][A-Za-z0-9_]*) +\"([A-Za-z0-9_ ,;\\.?!-]*)\" *(// *[A-Za-z0-9_ ,;\\.?!-]*)? *$");
        Matcher m1 = p1.matcher(cmd);
        if (m1.find()) {
            return true;
        }
        Pattern p2 = Pattern.compile(
                "^ *change +password +([A-Za-z][A-Za-z0-9_]*) +\"([A-Za-z0-9_ ,;\\.?!-]*)\" *(// *[A-Za-z0-9_ ,;\\.?!-]*)? *$");
        Matcher m2 = p2.matcher(cmd);
        if (m2.find()) {
            return true;
        }

        Pattern p3 = Pattern.compile("^ *set +([A-Za-z][A-Za-z0-9_]*) += +(.+)+ *(// *[A-Za-z0-9_ ,;\\.?!-]*)? *$");
        Matcher m3 = p3.matcher(cmd);
        if (m3.find()) {
            return true;
        }

        Pattern p4 = Pattern.compile("^ *local +([A-Za-z][A-Za-z0-9_]*) += +(.+)+ *(// *[A-Za-z0-9_ ,;\\.?!-]*)? *$");
        Matcher m4 = p4.matcher(cmd);
        if (m4.find()) {
            return true;
        }

        Pattern p5 = Pattern.compile("^ *if +(.+) +then (.+)+ *(// *[A-Za-z0-9_ ,;\\.?!-]*)? *$");
        Matcher m5 = p5.matcher(cmd);
        if (m5.find()) {
            return true;
        }

        Pattern p6 = Pattern.compile(
                "^ *set +delegation +([A-Za-z][A-Za-z0-9_]*) +([A-Za-z][A-Za-z0-9_]*) +(read|write|delegate|toggle) +-> +([A-Za-z][A-Za-z0-9_]*) *(// *[A-Za-z0-9_ ,;\\.?!-]*)? *$");
        Matcher m6 = p6.matcher(cmd);
        if (m6.find()) {
            return true;
        }

        Pattern p7 = Pattern.compile(
                "^ *delete +delegation +([A-Za-z][A-Za-z0-9_]*) +([A-Za-z][A-Za-z0-9_]*) +(read|write|delegate|toggle) +-> +([A-Za-z][A-Za-z0-9_]*) *(// *[A-Za-z0-9_ ,;\\.?!-]*)? *$");
        Matcher m7 = p7.matcher(cmd);
        if (m7.find()) {
            return true;
        }

        Pattern p8 = Pattern
                .compile("^ *default +delegator += +([A-Za-z][A-Za-z0-9_]*) *(// *[A-Za-z0-9_ ,;\\.?!-]*)? *$");
        Matcher m8 = p8.matcher(cmd);
        if (m8.find()) {
            return true;
        }

        Pattern p9 = Pattern.compile("^ *print +(.*) *(// *[A-Za-z0-9_ ,;\\.?!-]*)? *$");
        Matcher m9 = p9.matcher(cmd);
        if (m9.find()) {
            return true;
        }

        Pattern p10 = Pattern.compile(
                "^ *set +rule +([A-Za-z][A-Za-z0-9_]*) += +if +(.*) +then +(.*) *(// *[A-Za-z0-9_ ,;\\.?!-]*)? *$");
        Matcher m10 = p10.matcher(cmd);
        if (m10.find()) {
            return true;
        }

        Pattern p11 = Pattern.compile("^ *activate +rule +([A-Za-z][A-Za-z0-9_]*) *(// *[A-Za-z0-9_ ,;\\.?!-]*)? *$");
        Matcher m11 = p11.matcher(cmd);
        if (m11.find()) {
            return true;
        }

        Pattern p12 = Pattern.compile("^ *deactivate +rule +([A-Za-z][A-Za-z0-9_]*) *(// *[A-Za-z0-9_ ,;\\.?!-]*)? *$");
        Matcher m12 = p12.matcher(cmd);
        if (m12.find()) {
            return true;
        }

        return false;
    }

    private String parse_rule_condition(String principal, String input) {
        String conditionRegex = "^ *(.*) +([=|>|<|>=|<=]) +(.*)";
        Pattern pattern = Pattern.compile(conditionRegex);
        Matcher matcher = pattern.matcher(input);
        if (matcher.find()) {
            String leftValue = matcher.group(1);
            String cond_operator = matcher.group(2);
            String rightValue = matcher.group(3);
            String parsedLeft = parse_rule_value(principal, leftValue);
            String parsedRight = parse_rule_value(principal, rightValue);

            if (parsedLeft.contains("SEC") || parsedRight.contains("SEC") || parsedLeft.contains("DENIED") || parsedRight.contains("DENIED")) {
                return "DENIED";
            } else if (parsedLeft.contains("FAIL") || parsedRight.contains("FAIL")) {
                //System.out.println("SOMETHING WENT WRONG WHEN PARSING VALUES FOR RULE");
                return "FAIL";
            } else {
                // //System.out.println("RULE EVAL OPERATOR IS: " + cond_operator);
                //System.out.println(String.format("RULE COND EVALUATION: LEFT: [%s] OP: [%s] RIGHT: [%s]", parsedLeft,
                //        cond_operator, parsedRight));
                if (cond_operator.equals("=")) {
                    return String.valueOf(Integer.parseInt(parsedLeft) == Integer.parseInt(parsedRight));
                } else if (cond_operator.equals(">")) {
                    return String.valueOf(Integer.parseInt(parsedLeft) > Integer.parseInt(parsedRight));
                } else if (cond_operator.equals("<")) {
                    return String.valueOf(Integer.parseInt(parsedLeft) < Integer.parseInt(parsedRight));
                } else if (cond_operator.equals(">=")) {
                    return String.valueOf(Integer.parseInt(parsedLeft) >= Integer.parseInt(parsedRight));
                } else if (cond_operator.equals("<=")) {
                    return String.valueOf(Integer.parseInt(parsedLeft) <= Integer.parseInt(parsedRight));
                } else {
                    return String.valueOf(false);
                }

            }
        } else {
            return String.valueOf(false);
        }
    }

    public List<RuleOutput> doRules() {
        List<RuleOutput> retValue = new LinkedList<RuleOutput>();
        for (Entry<String, Rule> entry : rules.entrySet()) {
            if (entry.getValue().isToggled()) {
                String rule_name = entry.getKey();
                Rule current_rule = entry.getValue();
                String condition_eval = parse_rule_condition(current_rule.getCreator(), current_rule.getCondition());
                if (condition_eval.contains("DENIED")) {
                    // condition had denied so send denied read
                    retValue.add(new RuleOutput(rule_name, "DENIED_READ"));
                    deactive_rule("admin", rule_name);
                } else if (condition_eval.contains("FAIL")) {
                    //System.out.println("FAIL_CONDITION_EVAL: " + condition_eval);
                    retValue.add(new RuleOutput(rule_name, "FAILED"));
                    deactive_rule("admin", rule_name);
                } else if (Boolean.parseBoolean(condition_eval) == false) {
                    // do not return anything if the condition is false
                    //System.out.println("RULE IS GOING TO DO NOTHING: " + rule_name);
                } else {
                    // rule condition is true so we should go ahead and run the rules
                    Parser p = new Parser(this);
                    p.setPrinciple(current_rule.getCreator());
                    p.parsePrimCmd(current_rule.getPrimitive_command());
                    if (p.output.size() > 1) {
                        //System.out.println("SOMETHING MESSED UP AND PARSING THE RULE HAD MORE THAN 1 OUTPUT");
                    } else {
                        Status parser_output = (Status) p.output.get(0);
                        String stat = parser_output.getStatus();
                        if (stat.contains("FAIL")) {
                            this.deactive_rule("admin", rule_name);
                            retValue.add(new RuleOutput(rule_name, stat));
                        } else if (stat.contains("DENIED")) {
                            retValue.add(new RuleOutput(rule_name, stat));
                            deactive_rule("admin", rule_name);
                        } else {
                            retValue.add(new RuleOutput(rule_name, stat));
                        }
                    }
                }

            }
        }

        return retValue;
    }

}
