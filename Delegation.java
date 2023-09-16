
import java.util.List;

import java.util.LinkedList;

public class Delegation{

    private List<ControllerState.RIGHT> rights;
    private String symbol;
    private String from;

    public Delegation(ControllerState.RIGHT right, String symbol, String from){
        LinkedList<ControllerState.RIGHT> toAdd = new LinkedList<ControllerState.RIGHT>();
        toAdd.add(right);
        this.rights = toAdd;
        this.symbol = symbol;
        this.from = from;
    }

    public Delegation(List<ControllerState.RIGHT> rights, String symbol, String from){
        this.rights = rights;
        this.symbol = symbol;
        this.from = from;
    }

    public List<ControllerState.RIGHT> getRights() {
        return rights;
    }

    //return a true and false value if the principle that owns this object has a specific right for this symbol
    public boolean hasRight(ControllerState.RIGHT right){
        return rights.contains(right);
    }

    public void addRight(ControllerState.RIGHT right) {
        if(rights.contains(right)){
            return;
        }
        else{
            rights.add(right);
        }
    }

    public void removeRight(ControllerState.RIGHT right){
        if(rights.contains(right)){
            rights.remove(right);
        }
        else{
            return;
        }
    }

    public String getSymbol() {
        return symbol;
    }


    public String getFrom() {
        return from;
    }

    public Delegation copy(){
        List<ControllerState.RIGHT> copiedRight = new LinkedList<ControllerState.RIGHT>();
        for(ControllerState.RIGHT temp : this.rights){
            copiedRight.add(temp);
        }
        return new Delegation(copiedRight,this.symbol,this.from);
    }

    public String toString(){
        return String.format("Symbol: [%s] From: [%s] RIGHTS:[%s]", this.symbol, this.from, this.rights);
    }
    
}