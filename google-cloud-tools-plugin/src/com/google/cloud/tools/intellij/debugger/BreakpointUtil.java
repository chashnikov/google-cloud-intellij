/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.tools.intellij.debugger;

import com.google.api.client.repackaged.com.google.common.base.Strings;
import com.google.api.services.clouddebugger.v2.model.StatusMessage;
import com.google.cloud.tools.intellij.util.GctBundle;
import com.google.common.collect.ImmutableList;

import com.intellij.openapi.diagnostic.Logger;

import org.jetbrains.annotations.Nullable;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import java.util.Collection;
import java.util.Date;

/**
 * Utility functions for cloud debug data.
 */
public class BreakpointUtil {

  private static final Logger LOG = Logger.getInstance(BreakpointUtil.class);

  // TODO(joaomartins): Check with API team on when the rollout to the NO_MS format is done,
  // so we can use only one parser.
  public static final Collection<DateTimeFormatter> FORMATS =
      ImmutableList.of(ISODateTimeFormat.dateTimeNoMillis(), ISODateTimeFormat.dateTime());

  /**
   * This is a helper routine that converts a server {@link StatusMessage} to descriptive text.
   */
  @Nullable
  public static String getUserErrorMessage(@Nullable StatusMessage statusMessage) {
    if (statusMessage == null || !Boolean.TRUE.equals(statusMessage.getIsError())) {
      return null;
    }

    String errorDescription = getUserMessage(statusMessage);
    return !Strings.isNullOrEmpty(errorDescription) ? errorDescription
        : GctBundle.getString("clouddebug.fallbackerrormessage");
  }

  /**
   * Formats and returns the user message.
   */
  @Nullable
  public static String getUserMessage(@Nullable StatusMessage statusMessage) {
    if (statusMessage != null && statusMessage.getDescription() != null) {
      String formatString = statusMessage.getDescription().getFormat();
      Integer idx = 0;
      // Parameters in the server version are encoded script style with '$'.
      String argString = "$" + idx.toString();
      while (formatString.contains(argString)) {
        formatString = formatString.replace(argString, "%s");
        idx++;
        argString = "$" + idx.toString();
      }
      return String.format(formatString, statusMessage.getDescription().getParameters());
    }
    return null;
  }

  /**
   * Parses a date time string to a {@link java.util.Date}.
   *
   * <p>This method is currently only needed because the CDB service is returning ambiguous DateTime
   * formats. https://github.com/GoogleCloudPlatform/google-cloud-intellij/issues/917.
   * When the root cause is fixed, we should be able to replace this method with direct invocations
   * to {@code DateTimeFormatter.parse()} or {@code java.time.ZonedDateTime.parse()}, if we're
   * using Java8.
   */
  @Nullable
  public static Date parseDateTime(@Nullable String dateString) {
    if (dateString == null) {
      return null;
    }

    for (DateTimeFormatter formatter : FORMATS) {
      try {
        return formatter.parseDateTime(dateString).toDate();
      } catch (IllegalArgumentException iae) {
        // Do nothing, try the next parser.
      }
    }
    LOG.error("datetime " + dateString + "couldn't be parsed by any formats.");

    return null;
  }
}
