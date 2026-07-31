package com.winpress.commercial.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "winpress")
public class WinPressProperties {
  private int sessionTtlHours = 12;
  private String storagePath = "../storage/uploads";
  private long storageMaxFileBytes = 20L * 1024 * 1024;
  private String corsOrigins = "http://localhost:5217,http://127.0.0.1:5217";
  private final Login login = new Login();
  private final Niumedia niumedia = new Niumedia();
  private final Federation federation = new Federation();

  public int getSessionTtlHours() { return sessionTtlHours; }
  public void setSessionTtlHours(int sessionTtlHours) { this.sessionTtlHours = sessionTtlHours; }
  public String getStoragePath() { return storagePath; }
  public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
  public long getStorageMaxFileBytes() { return storageMaxFileBytes; }
  public void setStorageMaxFileBytes(long storageMaxFileBytes) {
    this.storageMaxFileBytes = storageMaxFileBytes;
  }
  public String getCorsOrigins() { return corsOrigins; }
  public void setCorsOrigins(String corsOrigins) { this.corsOrigins = corsOrigins; }
  public Login getLogin() { return login; }
  public Niumedia getNiumedia() { return niumedia; }
  public Federation getFederation() { return federation; }

  public static class Federation {
    private boolean enabled = false;
    private boolean migrateOnStart = false;
    private String sharedSecret = "";
    private String platformIssuer = "niumedia-platform";
    private String winpressIssuer = "winpress-commercial";
    private String sourceInstanceId = "default";
    private String geoCallbackUrl = "";
    private int callbackTimeoutSeconds = 15;
    private int maxRequestsPerMinute = 120;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isMigrateOnStart() { return migrateOnStart; }
    public void setMigrateOnStart(boolean migrateOnStart) { this.migrateOnStart = migrateOnStart; }
    public String getSharedSecret() { return sharedSecret; }
    public void setSharedSecret(String sharedSecret) { this.sharedSecret = sharedSecret; }
    public String getPlatformIssuer() { return platformIssuer; }
    public void setPlatformIssuer(String platformIssuer) { this.platformIssuer = platformIssuer; }
    public String getWinpressIssuer() { return winpressIssuer; }
    public void setWinpressIssuer(String winpressIssuer) { this.winpressIssuer = winpressIssuer; }
    public String getSourceInstanceId() { return sourceInstanceId; }
    public void setSourceInstanceId(String sourceInstanceId) { this.sourceInstanceId = sourceInstanceId; }
    public String getGeoCallbackUrl() { return geoCallbackUrl; }
    public void setGeoCallbackUrl(String geoCallbackUrl) { this.geoCallbackUrl = geoCallbackUrl; }
    public int getCallbackTimeoutSeconds() { return callbackTimeoutSeconds; }
    public void setCallbackTimeoutSeconds(int callbackTimeoutSeconds) { this.callbackTimeoutSeconds = callbackTimeoutSeconds; }
    public int getMaxRequestsPerMinute() { return maxRequestsPerMinute; }
    public void setMaxRequestsPerMinute(int maxRequestsPerMinute) {
      this.maxRequestsPerMinute = maxRequestsPerMinute;
    }
  }

  public static class Login {
    private int maxFailures = 8;
    private int failureWindowSeconds = 900;
    private int cooldownSeconds = 300;

    public int getMaxFailures() { return maxFailures; }
    public void setMaxFailures(int maxFailures) { this.maxFailures = maxFailures; }
    public int getFailureWindowSeconds() { return failureWindowSeconds; }
    public void setFailureWindowSeconds(int failureWindowSeconds) {
      this.failureWindowSeconds = failureWindowSeconds;
    }
    public int getCooldownSeconds() { return cooldownSeconds; }
    public void setCooldownSeconds(int cooldownSeconds) { this.cooldownSeconds = cooldownSeconds; }
  }

  public static class Niumedia {
    private String baseUrl = "https://api.media.beer/v1";
    private String token = "";
    private String mediaSearchPath = "/media/search";
    private String reporterSearchPath = "/reporter/search";
    private String regionPath = "/region";
    private String mediaTypesPath = "/media/types";
    private String mediaFormsPath = "/media/mp_types";
    private int requestTimeoutSeconds = 15;
    private int searchCacheSeconds = 300;
    private int taxonomyCacheSeconds = 86400;
    /**
     * A small shared interval protects the licensed discovery endpoint from bursts caused by
     * multiple people working in the same project at once. It is deliberately configurable
     * because the permitted request rate belongs to the service agreement, not to the UI.
     */
    private int minRequestIntervalMillis = 500;
    /** Default cool-down used when the upstream does not return a Retry-After header. */
    private int rateLimitCooldownSeconds = 60;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getMediaSearchPath() { return mediaSearchPath; }
    public void setMediaSearchPath(String mediaSearchPath) { this.mediaSearchPath = mediaSearchPath; }
    public String getReporterSearchPath() { return reporterSearchPath; }
    public void setReporterSearchPath(String reporterSearchPath) { this.reporterSearchPath = reporterSearchPath; }
    public String getRegionPath() { return regionPath; }
    public void setRegionPath(String regionPath) { this.regionPath = regionPath; }
    public String getMediaTypesPath() { return mediaTypesPath; }
    public void setMediaTypesPath(String mediaTypesPath) { this.mediaTypesPath = mediaTypesPath; }
    public String getMediaFormsPath() { return mediaFormsPath; }
    public void setMediaFormsPath(String mediaFormsPath) { this.mediaFormsPath = mediaFormsPath; }
    public int getRequestTimeoutSeconds() { return requestTimeoutSeconds; }
    public void setRequestTimeoutSeconds(int requestTimeoutSeconds) {
      this.requestTimeoutSeconds = requestTimeoutSeconds;
    }
    public int getSearchCacheSeconds() { return searchCacheSeconds; }
    public void setSearchCacheSeconds(int searchCacheSeconds) { this.searchCacheSeconds = searchCacheSeconds; }
    public int getTaxonomyCacheSeconds() { return taxonomyCacheSeconds; }
    public void setTaxonomyCacheSeconds(int taxonomyCacheSeconds) {
      this.taxonomyCacheSeconds = taxonomyCacheSeconds;
    }
    public int getMinRequestIntervalMillis() { return minRequestIntervalMillis; }
    public void setMinRequestIntervalMillis(int minRequestIntervalMillis) {
      this.minRequestIntervalMillis = minRequestIntervalMillis;
    }
    public int getRateLimitCooldownSeconds() { return rateLimitCooldownSeconds; }
    public void setRateLimitCooldownSeconds(int rateLimitCooldownSeconds) {
      this.rateLimitCooldownSeconds = rateLimitCooldownSeconds;
    }
  }
}
