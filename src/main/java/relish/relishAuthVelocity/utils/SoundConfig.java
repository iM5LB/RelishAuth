package relish.relishAuthVelocity.utils;

import java.util.List;
import java.util.Map;
import relish.relishAuthVelocity.config.Config;

public class SoundConfig {
    public static Sound[] getSuccessSounds(Config config) {
        try {
            Object successSounds = config.get("customization.limbo.sounds.success");
            if (successSounds instanceof List) {
                List soundList = (List)successSounds;
                Sound[] sounds = new Sound[soundList.size()];
                for (int i = 0; i < soundList.size(); ++i) {
                    if (!(soundList.get(i) instanceof Map)) continue;
                    Map soundData = (Map)soundList.get(i);
                    String soundKey = String.valueOf(soundData.get("sound"));
                    float volume = SoundConfig.getFloat(soundData.get("volume"), 0.5f);
                    float pitch = SoundConfig.getFloat(soundData.get("pitch"), 1.0f);
                    sounds[i] = new Sound(soundKey, volume, pitch);
                }
                return sounds;
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return new Sound[]{new Sound("minecraft:entity.player.levelup", 0.5f, 1.0f), new Sound("minecraft:entity.experience_orb.pickup", 0.5f, 1.5f)};
    }

    public static Sound getErrorSound(Config config) {
        try {
            Object errorSound = config.get("customization.limbo.sounds.error");
            if (errorSound instanceof Map) {
                Map soundData = (Map)errorSound;
                String soundKey = String.valueOf(soundData.get("sound"));
                float volume = SoundConfig.getFloat(soundData.get("volume"), 0.5f);
                float pitch = SoundConfig.getFloat(soundData.get("pitch"), 0.5f);
                return new Sound(soundKey, volume, pitch);
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return new Sound("minecraft:block.note_block.bass", 0.5f, 0.5f);
    }

    private static float getFloat(Object value, float defaultValue) {
        if (value instanceof Number) {
            return ((Number)value).floatValue();
        }
        return defaultValue;
    }

    public static class Sound {
        public final String sound;
        public final float volume;
        public final float pitch;

        public Sound(String sound, float volume, float pitch) {
            this.sound = sound;
            this.volume = volume;
            this.pitch = pitch;
        }
    }
}
