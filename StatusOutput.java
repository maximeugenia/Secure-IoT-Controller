package outputs;
public class StatusOutput{
    private String status;
    private String output;

    public StatusOutput(String status, String output) {
        this.status = status;
        this.output = output;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public String toString(){
        return this.status + " = " + this.output;
    }
    
}