package outputs;
public class RuleOutput{
    private String rule;
    private String status;

    public RuleOutput(String rule, String status){
        this.rule = rule;
        this.status = status;
    }

    public String getRule() {
        return rule;
    }

    public void setRule(String rule) {
        this.rule = rule;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String toString(){
        return this.rule + " - " + this.status;
    }
    
}