/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.tools.intellij.appengine.server.instance;

import com.google.cloud.tools.intellij.appengine.util.AppEngineUtil;
import com.google.cloud.tools.intellij.util.GctBundle;
import com.google.common.base.Joiner;

import com.intellij.javaee.run.configuration.CommonModel;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.impl.run.BuildArtifactsBeforeRunTaskProvider;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.components.JBLabel;

import org.jetbrains.annotations.NotNull;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * @author nik
 */
public class AppEngineRunConfigurationEditor extends SettingsEditor<CommonModel> implements
    PanelWithAnchor {

  private JPanel myMainPanel;
  private JComboBox myArtifactComboBox;
  private JTextField port;
  private RawCommandLineEditor jvmFlags;
  private JBLabel myWebArtifactToDeployLabel;
  private JBLabel myPortLabel;
  private JBLabel myServerParametersLabel;
  private final Project myProject;
  private Artifact myLastSelectedArtifact;
  private JComponent anchor;
  private JTextField authDomain;
  private JTextField storagePath;
  private JTextField adminHost;
  private JTextField adminPort;
  private JTextField apiPort;
  private JTextField host;
  private JComboBox logLevel;
  private JCheckBox useMtimeFileWatcher;
  private JTextField threadsafeOverride;
  private JCheckBox allowSkippedFiles;
  private JCheckBox automaticRestart;
  private JComboBox devappserverLogLevel;
  private JCheckBox skipSdkUpdateCheck;
  private JTextField gcsBucketName;
  // TODO(joaomartins): Change "Advanced Settings" to a collapsable drop down, like Before Launch.

  public AppEngineRunConfigurationEditor(Project project) {
    myProject = project;
    myArtifactComboBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent event) {
        onArtifactChanged();
      }
    });

    setAnchor(myWebArtifactToDeployLabel);
  }

  private void onArtifactChanged() {
    final Artifact selectedArtifact = getSelectedArtifact();
    if (!Comparing.equal(myLastSelectedArtifact, selectedArtifact)) {
      if (myLastSelectedArtifact != null) {
        BuildArtifactsBeforeRunTaskProvider
            .setBuildArtifactBeforeRunOption(myMainPanel, myProject, myLastSelectedArtifact, false);
      }
      if (selectedArtifact != null) {
        BuildArtifactsBeforeRunTaskProvider
            .setBuildArtifactBeforeRunOption(myMainPanel, myProject, selectedArtifact, true);
      }
      myLastSelectedArtifact = selectedArtifact;
    }
  }

  /**
   * Resets the configuration editor form using the settings in the server model. The following
   * settings have been omitted from the form:
   * <ul>
   * <li> maxModuleInstances - we set this on behalf of the user to prevent breaking the dev app
   * server in debug mode. See
   * <a href="https://github.com/GoogleCloudPlatform/google-cloud-intellij/issues/928">#928</a>
   * </li>
   * </ul>
   */
  protected void resetEditorFrom(CommonModel commonModel) {
    final AppEngineServerModel serverModel = (AppEngineServerModel) commonModel.getServerModel();
    final Artifact artifact = serverModel.getArtifact();
    myArtifactComboBox.setSelectedItem(artifact);
    port.setText(intToString(serverModel.getPort()));
    host.setText(serverModel.getHost());
    adminHost.setText(serverModel.getAdminHost());
    adminPort.setText(intToString(serverModel.getAdminPort()));
    authDomain.setText(serverModel.getAuthDomain());
    storagePath.setText(serverModel.getStoragePath());
    logLevel.setSelectedItem(serverModel.getLogLevel());
    useMtimeFileWatcher.setSelected(serverModel.getUseMtimeFileWatcher());
    threadsafeOverride.setText(serverModel.getThreadsafeOverride());
    jvmFlags.setDialogCaption(GctBundle.getString("appengine.run.jvmflags.title"));
    jvmFlags.setText(Joiner.on(AppEngineServerModel.JVM_FLAG_DELIMITER)
        .join(serverModel.getJvmFlags()));
    allowSkippedFiles.setSelected(serverModel.getAllowSkippedFiles());
    apiPort.setText(intToString(serverModel.getApiPort()));
    automaticRestart.setSelected(serverModel.getAutomaticRestart());
    devappserverLogLevel.setSelectedItem(serverModel.getDevAppserverLogLevel());
    skipSdkUpdateCheck.setSelected(serverModel.getSkipSdkUpdateCheck());
    gcsBucketName.setText(serverModel.getDefaultGcsBucketName());
  }

  protected void applyEditorTo(CommonModel commonModel) throws ConfigurationException {
    final AppEngineServerModel serverModel = (AppEngineServerModel) commonModel.getServerModel();
    serverModel.setPort(validateInteger(port.getText(), "port"));
    serverModel.setArtifact(getSelectedArtifact());

    serverModel.setHost(host.getText());
    serverModel.setAdminHost(adminHost.getText());
    if (!adminPort.getText().isEmpty()) {
      serverModel.setAdminPort(validateInteger(adminPort.getText(), "admin port"));
    }
    serverModel.setAuthDomain(authDomain.getText());
    serverModel.setStoragePath(storagePath.getText());
    serverModel.setLogLevel((String) logLevel.getSelectedItem());
    serverModel.setUseMtimeFileWatcher(useMtimeFileWatcher.isSelected());
    serverModel.setThreadsafeOverride(threadsafeOverride.getText());
    serverModel.setJvmFlags(jvmFlags.getText());
    serverModel.setAllowSkippedFiles(allowSkippedFiles.isSelected());
    if (!apiPort.getText().isEmpty()) {
      serverModel.setApiPort(validateInteger(apiPort.getText(), "API port"));
    }
    serverModel.setAutomaticRestart(automaticRestart.isSelected());
    serverModel.setDevAppserverLogLevel((String) devappserverLogLevel.getSelectedItem());
    serverModel.setSkipSdkUpdateCheck(skipSdkUpdateCheck.isSelected());
    serverModel.setDefaultGcsBucketName(gcsBucketName.getText());
  }

  private Integer validateInteger(String intText, String description)
      throws ConfigurationException {
    try {
      return Integer.parseInt(intText);
    } catch (NumberFormatException nfe) {
      throw new ConfigurationException(
          "'" + intText + "' is not a valid " + description + " number.");
    }
  }

  private String intToString(Integer value) {
    return value != null ? String.valueOf(value) : "";
  }

  private Artifact getSelectedArtifact() {
    return (Artifact) myArtifactComboBox.getSelectedItem();
  }

  @NotNull
  protected JComponent createEditor() {
    AppEngineUtil.setupAppEngineArtifactCombobox(myProject, myArtifactComboBox, false);
    return myMainPanel;
  }

  @Override
  public JComponent getAnchor() {
    return anchor;
  }

  @Override
  public void setAnchor(JComponent anchor) {
    this.anchor = anchor;
    myWebArtifactToDeployLabel.setAnchor(anchor);
    myPortLabel.setAnchor(anchor);
    myServerParametersLabel.setAnchor(anchor);
  }
}
