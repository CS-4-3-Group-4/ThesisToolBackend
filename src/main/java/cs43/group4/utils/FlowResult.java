package cs43.group4.utils;

public class FlowResult {
    public String classId;
    public String className;
    public String fromId;
    public String fromName;
    public String toId;
    public String toName;
    public long amount;

    public FlowResult(
            String classId, String className, String fromId, String fromName, String toId, String toName, long amount) {
        this.classId = classId;
        this.className = className;
        this.fromId = fromId;
        this.fromName = fromName;
        this.toId = toId;
        this.toName = toName;
        this.amount = amount;
    }
}
