package net.tawacentral.roger.secrets;

/**
 * Represents an Online Backup Application.
 * @author Ryan Dearing
 */
public class OnlineBackupApplication {
    private String displayName;
    private String classId;

  public OnlineBackupApplication(String displayName, String classId) {
        if(displayName == null || classId == null || displayName.equals("") || classId.equals("")) {
            throw new IllegalArgumentException("displayName and classId are required!");
        }
        this.displayName = displayName;
        this.classId = classId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getClassId() {
        return classId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        OnlineBackupApplication that = (OnlineBackupApplication) o;

        if (!classId.equals(that.classId)) return false;
        return displayName.equals(that.displayName);
    }

    @Override
    public int hashCode() {
        int result = displayName.hashCode();
        result = 31 * result + classId.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
