package com.spawngate.data;

import java.util.UUID;

public class PlayerData {

    private final UUID uuid;
    private String username;
    private boolean rulesCompleted;
    private int currentRulePage;
    private boolean verified;
    private long playtimeSeconds;

    // Данные сессии (в памяти)
    private long sessionJoinTimestamp;
    private long pageOpenTimestamp;
    private long lastWarningTimestamp;

    public PlayerData(UUID uuid, String username, boolean rulesCompleted, int currentRulePage, boolean verified, long playtimeSeconds) {
        this.uuid = uuid;
        this.username = username;
        this.rulesCompleted = rulesCompleted;
        this.currentRulePage = Math.max(1, currentRulePage);
        this.verified = verified;
        this.playtimeSeconds = playtimeSeconds;
        this.sessionJoinTimestamp = System.currentTimeMillis();
        this.pageOpenTimestamp = System.currentTimeMillis();
        this.lastWarningTimestamp = 0;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public boolean isRulesCompleted() {
        return rulesCompleted;
    }

    public void setRulesCompleted(boolean rulesCompleted) {
        this.rulesCompleted = rulesCompleted;
    }

    public int getCurrentRulePage() {
        return currentRulePage;
    }

    public void setCurrentRulePage(int currentRulePage) {
        this.currentRulePage = currentRulePage;
        this.pageOpenTimestamp = System.currentTimeMillis();
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public long getPlaytimeSeconds() {
        return playtimeSeconds;
    }

    public void setPlaytimeSeconds(long playtimeSeconds) {
        this.playtimeSeconds = playtimeSeconds;
    }

    public void addPlaytimeSeconds(long seconds) {
        this.playtimeSeconds += seconds;
    }

    public long getSessionJoinTimestamp() {
        return sessionJoinTimestamp;
    }

    public void setSessionJoinTimestamp(long sessionJoinTimestamp) {
        this.sessionJoinTimestamp = sessionJoinTimestamp;
    }

    public long getPageOpenTimestamp() {
        return pageOpenTimestamp;
    }

    public void resetPageOpenTimestamp() {
        this.pageOpenTimestamp = System.currentTimeMillis();
    }

    /**
     * Сколько секунд игрок уже находится на текущей странице правил
     */
    public long getSecondsOnCurrentPage() {
        return (System.currentTimeMillis() - pageOpenTimestamp) / 1000L;
    }

    /**
     * Проверка кулдауна на отправку предупреждения на спавне
     */
    public boolean checkAndSetWarningCooldown(long cooldownMillis) {
        long now = System.currentTimeMillis();
        if (now - lastWarningTimestamp >= cooldownMillis) {
            lastWarningTimestamp = now;
            return true;
        }
        return false;
    }
}
