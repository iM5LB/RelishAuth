package relish.relishAuthVelocity.integrations;

public record DiscordUserSearchResult(String userId, boolean inGuild, boolean found) {
    public static DiscordUserSearchResult notFound() {
        return new DiscordUserSearchResult(null, false, false);
    }

    public static DiscordUserSearchResult foundInGuild(String userId) {
        return new DiscordUserSearchResult(userId, true, true);
    }

    public static DiscordUserSearchResult foundNotInGuild(String userId) {
        return new DiscordUserSearchResult(userId, false, true);
    }
}
