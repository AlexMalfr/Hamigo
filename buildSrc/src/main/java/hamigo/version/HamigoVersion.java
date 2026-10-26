package hamigo.version;

/** A single increasing build number across all public tracks, independent of Git history. */
public record HamigoVersion(String name, int code, String channel, String revision, boolean dirty) {
    public static HamigoVersion create(String product, int build, String channel,
                                      int prerelease, String revision, boolean dirty) {
        if (!product.matches("[1-9][0-9]*\\.[0-9]+(?:\\.[0-9]+)?"))
            throw new IllegalArgumentException("version must look like 1.0 or 1.0.1");
        if (build < 1 || build > 2100000000 || prerelease < 1)
            throw new IllegalArgumentException("buildNumber: 1..2100000000; prereleaseNumber > 0");
        if (!revision.matches("[a-f0-9]{7,40}|local"))
            throw new IllegalArgumentException("Invalid source revision");
        String name = switch (channel) {
            case "release" -> product;
            case "alpha", "beta" -> product + "-" + channel + prerelease;
            case "dev" -> product + "-dev" + build + "+" + revision + (dirty ? "-dirty" : "");
            default -> throw new IllegalArgumentException("hamigoChannel must be dev, alpha, beta or release");
        };
        // Keep updates compatible with dev APKs from the initial preparation; dev is never uploaded.
        int code = channel.equals("dev") ? Math.max(570001, build) : build;
        return new HamigoVersion(name, code, channel, revision, dirty);
    }
    public boolean isPublic() { return !channel.equals("dev"); }
}
