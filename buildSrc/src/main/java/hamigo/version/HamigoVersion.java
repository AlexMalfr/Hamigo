package hamigo.version;

/** One technical sequence per product release, shared by all public tracks. */
public record HamigoVersion(String name, int code, String channel, String revision, boolean dirty) {
    public static HamigoVersion create(String product, int ordinal, int build, String channel,
                                      int prerelease, String revision, boolean dirty) {
        if (!product.matches("[1-9][0-9]*\\.[0-9]+(?:\\.[0-9]+)?"))
            throw new IllegalArgumentException("version must look like 1.0 or 1.0.1");
        if (ordinal < 1 || ordinal > 209999 || build < 1 || build > 9999 || prerelease < 1)
            throw new IllegalArgumentException("releaseOrdinal: 1..209999; buildNumber: 1..9999; prereleaseNumber > 0");
        if (!revision.matches("[a-f0-9]{7,40}|local"))
            throw new IllegalArgumentException("Invalid source revision");
        String name = switch (channel) {
            case "release" -> product;
            case "alpha", "beta" -> product + "-" + channel + prerelease;
            case "dev" -> product + "-dev" + build + "+" + revision + (dirty ? "-dirty" : "");
            default -> throw new IllegalArgumentException("hamigoChannel must be dev, alpha, beta or release");
        };
        return new HamigoVersion(name, ordinal * 10000 + build, channel, revision, dirty);
    }
    public boolean isPublic() { return !channel.equals("dev"); }
}
