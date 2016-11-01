/*
 * Copyright 2015 JIHU, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/
package org.giiwa.core.base;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteStreamHandler;
import org.apache.commons.exec.OS;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.giiwa.core.bean.UID;
import org.giiwa.core.bean.X;
import org.giiwa.framework.web.Language;
import org.giiwa.framework.web.Module;

/**
 * The {@code Shell} Class lets run shell command.
 *
 * @author joe
 */
public class Shell {

  /** The log. */
  static Log log = LogFactory.getLog(Shell.class);

  public static enum Logger {
    error("ERROR"), warn("WARN"), info("INFO");

    String level;

    /**
     * Instantiates a new logger.
     *
     * @param s
     *          the s
     */
    Logger(String s) {
      this.level = s;
    }

  };

  /**
   * Log.
   *
   * @param ip
   *          the ip
   * @param level
   *          the level
   * @param module
   *          the module
   * @param message
   *          the message
   */
  // 192.168.1.1#系统名称#2014-10-31#ERROR#日志消息#程序名称
  public static void log(String ip, Logger level, String module, String message) {
    String deli = Module.home.get("log_deli", "#");
    StringBuilder sb = new StringBuilder();
    sb.append(ip).append(deli);
    sb.append("support").append(deli);
    sb.append(Language.getLanguage().format(System.currentTimeMillis(), "yyyy-MM-dd hh:mm:ss"));
    sb.append(deli).append(level.name()).append(deli).append(message).append(deli).append(module);

    try {
      Shell.run("logger " + level.level + deli + sb.toString());
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static boolean isLinux() {
    return OS.isFamilyUnix();
  }

  private static int _ubuntu = -1;

  public static boolean isUbuntu() {
    if (_ubuntu == -1) {
      try {
        String uname = Shell.run("uname -a");
        _ubuntu = uname.indexOf("Ubuntu") > -1 ? 1 : 0;
      } catch (Exception e) {
        return false;
      }
    }
    return _ubuntu == 1;
  }

  /**
   * run a command with the out, err and in
   * 
   * @param cmd
   *          the command line
   * @param out
   *          the console outputstream
   * @param err
   *          the error outputstream
   * @param in
   *          the inputstream
   * @return the result
   * @throws ExecuteException
   * @throws IOException
   */
  public static int run(String cmd, OutputStream out, OutputStream err, InputStream in, String workdir)
      throws IOException {

    CommandLine cmdLine = CommandLine.parse(cmd);
    DefaultExecutor executor = new DefaultExecutor();
    ExecuteStreamHandler stream = new PumpStreamHandler(out, err, in);
    executor.setExitValues(new int[] { 0, 1, -1 });

    executor.setStreamHandler(stream);
    if (!X.isEmpty(workdir)) {
      executor.setWorkingDirectory(new File(workdir));
    }

    // ExecuteWatchdog watchdog = new ExecuteWatchdog(60000);
    // executor.setWatchdog(watchdog);
    // watchdog.destroyProcess();
    return executor.execute(cmdLine);

  }

  /**
   * run command, and return the console output
   * 
   * @param cmd
   *          the command line
   * @return the output of console and error
   * @throws IOException
   */
  public static String run(String cmd) throws IOException {
    return run(cmd, null);
  }

  /**
   * run the command in workdir
   * 
   * @param cmd
   *          the command
   * @param workdir
   *          the path
   * @return the output of console and error
   * @throws IOException
   */
  public static String run(String cmd, String workdir) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    run(cmd, out, out, null, workdir);
    out.close();
    return out.toString();
  }

  /**
   * get the status of the processname
   * 
   * @param processname
   *          the process name
   * @return the status of the process
   * @throws IOException
   */
  public static String getStatus(String processname) throws IOException {
    if (isLinux() || OS.isFamilyMac()) {

      String line = "ps -ef | grep " + processname;
      return run(line);

    } else if (OS.isFamilyWindows()) {

      String cmd = "tasklist /nh /FI \"IMAGENAME eq " + processname + "\"";
      return run(cmd);

    } else {
      throw new IOException("not support");
    }

  }

  public static void kill(String processname) throws IOException {
    if (isLinux() || OS.isFamilyMac()) {
      String line = "kill -9 `ps -ef | grep " + processname + " | awk '{print $2}'`;";

      // Create a tmp file. Write permissions!?
      File f = new File(UID.random(10) + ".bash");
      FileUtils.writeStringToFile(f, line);

      // Execute the file we just creted. No flags are due if it is
      // executed with bash directly
      CommandLine commandLine = CommandLine.parse("bash " + f.getName());

      DefaultExecutor executor = new DefaultExecutor();
      executor.execute(commandLine);
      f.delete();
    } else if (OS.isFamilyWindows()) {

      String cmd = "tasklist /nh /FI \"IMAGENAME eq " + processname + "\"";

      String line = run(cmd);
      String[] lineArray = line.split(" ");
      String pid = lineArray[17].trim();
      run("taskkill /F /PID " + pid);

    } else {
      throw new IOException("not support");
    }
  }

  public static void main(String[] args) {
    try {
      System.out.println(run("uname -a"));

      System.out.println();

    } catch (Exception e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

}
