
public class Rule{
    private String name;
    private String condition;
    private String primitive_command;
    private boolean toggled;
    private String creator;

    public Rule(String name, String condition, String primitive_command, String creator){
        this.name = name;
        this.condition = condition;
        this.primitive_command = primitive_command;
        this.toggled = false;
        this.creator = creator;
    }

    public String getName() {
        return name;
    }

    public String getCreator(){
        return this.creator;
    }


    public String getCondition() {
        return condition;
    }


    public String getPrimitive_command() {
        return primitive_command;
    }

    public void toggle(){
        toggled = !toggled;
    }

    public boolean isToggled(){
        return toggled;
        
    }
    


}