package me.advait.swapper.util;

public final class TimeFormat {
  private TimeFormat() {}

  public static String formatLong(long totalSeconds) {
    if (totalSeconds < 1) {
      totalSeconds = 1;
    }

    long minutes = totalSeconds / 60;
    long seconds = totalSeconds % 60;
    StringBuilder sb = new StringBuilder("Swapping in ");
    if (minutes > 0) {
      sb.append(minutes).append(minutes == 1 ? " minute" : " minutes");
      if (seconds > 0) {
        sb.append(", ").append(seconds).append(seconds == 1 ? " second" : " seconds");
      }
    } else {
      sb.append(seconds).append(seconds == 1 ? " second" : " seconds");
    }

    return sb.append("!").toString();
  }

  public static String formatShort(int seconds) {
    if (seconds < 1) {
      seconds = 1;
    }

    return "Swapping in " + seconds + (seconds == 1 ? " second!" : " seconds!");
  }
}
