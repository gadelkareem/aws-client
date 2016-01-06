package com.gadelkareem.awsclient.application;


import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.regions.Regions;
import com.amazonaws.regions.ServiceAbbreviations;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.Tag;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.DirectoryChooser;
import javafx.util.Callback;

import java.awt.*;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.prefs.Preferences;


public class Controller {


    public TableView tableView;
    public ChoiceBox regionMenu;
    public MenuItem launchShell;
    public MenuItem refreshTable;

    //Preferences form
    public GridPane preferencesForm;
    public TextField preferencesAccessKey;
    public TextField preferencesSecretKey;
    public CheckBox preferencesDisplayLoad;
    public TextField preferencesKeysPath;
    public TextField preferencesEc2User;

    private Preferences userPreferences = Preferences.userNodeForPackage(getClass());
    private String defaultRegion = Regions.EU_WEST_1.getName();
    private AWSCredentials awsCredentials;

    private List<ObservableList<StringProperty>> rows = new ArrayList<ObservableList<StringProperty>>();
    private List<String> columns = new ArrayList<String>();


    //INITIALIZE
    @FXML
    void initialize() {
        if (!hasPreferences()) {
            awsCredentials = new DefaultAWSCredentialsProviderChain().getCredentials();
            initPreferences();
            return;
        }
        initView();
    }

    private void initView() {
        awsCredentials = new BasicAWSCredentials(userPreferences.get("aws.access_key", ""), userPreferences.get("aws.secret_key", ""));
        initRegionsMenu();
        initEc2View();
        initContextMenu();
    }

    private boolean hasPreferences() {
        return !(userPreferences.get("aws.access_key", "").isEmpty() && userPreferences.get("aws.secret_key", "").isEmpty());
    }

    @FXML
    private void initPreferences() {
        tableView.setVisible(false);
        preferencesAccessKey.setText(userPreferences.get("aws.access_key", awsCredentials.getAWSAccessKeyId()));
        preferencesSecretKey.setText(userPreferences.get("aws.secret_key", awsCredentials.getAWSSecretKey()));
        preferencesEc2User.setText(userPreferences.get("aws.ec2_username", "ec2-user"));
        preferencesKeysPath.setText(userPreferences.get("aws.keys_path", getDefaultKeysPath()));
        preferencesDisplayLoad.selectedProperty().setValue(userPreferences.getBoolean("view.column.load", false));
        preferencesForm.setVisible(true);
    }

    @FXML
    private void savePreferences() {
        userPreferences.put("aws.access_key", preferencesAccessKey.getText());
        userPreferences.put("aws.secret_key", preferencesSecretKey.getText());
        userPreferences.put("aws.ec2_username", preferencesEc2User.getText());
        userPreferences.put("aws.keys_path", preferencesKeysPath.getText());
        userPreferences.putBoolean("view.column.load", preferencesDisplayLoad.isSelected());
        preferencesForm.setVisible(false);
        initView();
    }

    private void initContextMenu() {
        launchShell.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent event) {
                final int selectedIndex = tableView.getSelectionModel().getSelectedIndex();
                final ObservableList<StringProperty> selectedRow = rows.get(selectedIndex);
                final int publicDnsNameIndex = columns.indexOf("Public DNS Name");
                final int keyNameIndex = columns.indexOf("Key Name");
                try {
                    final ProcessBuilder processBuilder = new ProcessBuilder("/usr/bin/osascript",
                            "-e", "tell app \"Terminal\"",
                            "-e", "set currentTab to do script " +
                            "(\"/usr/bin/ssh -o CheckHostIP=no -o TCPKeepAlive=yes -o StrictHostKeyChecking=no -o ServerAliveInterval=120 -o ServerAliveCountMax=100 -i " +
                            userPreferences.get("aws.keys_path", getDefaultKeysPath()) + File.separator + selectedRow.get(keyNameIndex).getValue() + ".pem " +
                            userPreferences.get("aws.ec2_username", "ec2-user") + "@" +
                            selectedRow.get(publicDnsNameIndex).getValue() + "\")",
                            "-e", "end tell");
                    final Process process = processBuilder.start();
                    process.waitFor();
                } catch (Exception e) {
                    error(e.getMessage(), stackTraceToString(e));
                }

            }
        });
        refreshTable.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent event) {
                initEc2View();
            }
        });
    }

    private void initRegionsMenu() {
        try {
            regionMenu.getItems().addAll(RegionUtils.getRegions());
            regionMenu.getSelectionModel().select(Region.getRegion(Regions.fromName(userPreferences.get("aws.region", defaultRegion))));
        } catch (Exception e) {
            error(e.getMessage(), stackTraceToString(e));
            userPreferences.put("aws.region", defaultRegion);
        }
    }

    private void initEc2View() {
        try {
            Region region = (Region) regionMenu.getSelectionModel().getSelectedItem();
            AmazonEC2 amazonEC2Client = new AmazonEC2Client(awsCredentials);
            AmazonCloudWatchClient cloudWatchClient = new AmazonCloudWatchClient(awsCredentials);

            amazonEC2Client.setRegion(region);
            cloudWatchClient.setEndpoint(region.getServiceEndpoint(ServiceAbbreviations.CloudWatch));

            String firstColumnKey = "Name";

            columns.removeAll(columns);
            rows.removeAll(rows);


            columns.add(firstColumnKey);
            columns.add("Instance ID");
            if (userPreferences.getBoolean("view.column.load", false)) {
                columns.add("Instance Load");
            }
            columns.add("Security Group");
            columns.add("Instance Type");
            columns.add("Instance State");
            columns.add("Public DNS Name");
            columns.add("Public IP");
            columns.add("Private IP");
            columns.add("Key Name");
            columns.add("Instance Type");


            boolean hasFirstColumnKey = false;
            int maxTagsCount = 0;
            reservations:
            {
                for (Reservation reservation :
                        amazonEC2Client.describeInstances(new DescribeInstancesRequest()).getReservations()) {
                    for (Instance instance : reservation.getInstances()) {


                        ObservableList<StringProperty> row = FXCollections.observableArrayList();
                        row.add(new SimpleStringProperty(""));
                        row.add(new SimpleStringProperty(instance.getInstanceId()));
                        if (userPreferences.getBoolean("view.column.load", false)) {
                            String instanceLoad = Double.toString(monitorInstance(cloudWatchClient, instance.getInstanceId().toString()));
                            row.add(new SimpleStringProperty(instanceLoad));
                        }
                        row.add(new SimpleStringProperty(instance.getSecurityGroups().get(0).getGroupName()));
                        row.add(new SimpleStringProperty(instance.getInstanceType()));
                        row.add(new SimpleStringProperty(instance.getState().getName()));
                        row.add(new SimpleStringProperty(instance.getPublicDnsName()));
                        row.add(new SimpleStringProperty(instance.getPublicIpAddress()));
                        row.add(new SimpleStringProperty(instance.getPrivateIpAddress()));
                        row.add(new SimpleStringProperty(instance.getKeyName()));
                        row.add(new SimpleStringProperty(instance.getInstanceType()));

                        maxTagsCount = instance.getTags().size() > maxTagsCount ? instance.getTags().size() : maxTagsCount;
                        for (int i = 0; i < maxTagsCount; i++) {
                            row.add(new SimpleStringProperty(""));
                        }
                        for (Tag tag : instance.getTags()) {
                            if (tag.getKey().equals(firstColumnKey)) {
                                row.set(0, new SimpleStringProperty(tag.getValue()));
                                hasFirstColumnKey = true;
                            } else {
                                String columnHeader = "Tag::" + tag.getKey();
                                if (!columns.contains(columnHeader))
                                    columns.add(columnHeader);
                                row.set(columns.indexOf(columnHeader), new SimpleStringProperty(tag.getValue()));
                            }
                        }

                        rows.add(row);
                    }
                }
            }

            if (!hasFirstColumnKey) {
                columns.remove(0);
                for (ObservableList row : rows) {
                    row.remove(0);
                }
            }


            tableView.getItems().clear();
            tableView.getColumns().clear();

            for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
                tableView.getColumns().addAll(createColumn(columnIndex, columns.get(columnIndex)));
            }

            tableView.getItems().addAll(rows);
            tableView.setColumnResizePolicy(new Callback<TableView.ResizeFeatures, Boolean>() {
                public Boolean call(TableView.ResizeFeatures p) {
                    return true;
                }
            });
            tableView.setVisible(true);
        } catch (Exception e) {
            error(e.getMessage(), stackTraceToString(e));
            initPreferences();
        }

    }

    private TableColumn<ObservableList<StringProperty>, String> createColumn(
            final int columnIndex,
            String columnTitle
    ) {

        TableColumn<ObservableList<StringProperty>, String> column = new TableColumn<ObservableList<StringProperty>, String>();
        column.setText(columnTitle);

        column.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<ObservableList<StringProperty>, String>, ObservableValue<String>>() {
            public ObservableValue<String> call(
                    TableColumn.CellDataFeatures<ObservableList<StringProperty>, String> cellDataFeatures) {
                ObservableList<StringProperty> values = cellDataFeatures.getValue();
                if (columnIndex >= values.size()) {
                    return new SimpleStringProperty("");
                } else {
                    return cellDataFeatures.getValue().get(columnIndex);
                }
            }


        });
        return column;
    }

    private double monitorInstance(AmazonCloudWatchClient cloudWatchClient, String instanceId) {
        try {

            long offsetInMilliseconds = 1000 * 60 * 60 * 24;
            GetMetricStatisticsRequest request = new GetMetricStatisticsRequest()
                    .withStartTime(new Date(new Date().getTime() - offsetInMilliseconds))
                    .withNamespace("AWS/EC2")
                    .withPeriod(60 * 60)
                    .withDimensions(new Dimension().withName("InstanceId").withValue(instanceId))
                    .withMetricName("CPUUtilization")
                    .withStatistics("Average", "Maximum")
                    .withEndTime(new Date());
            GetMetricStatisticsResult getMetricStatisticsResult = cloudWatchClient.getMetricStatistics(request);

            double avgCPUUtilization = 0;
            List dataPoint = getMetricStatisticsResult.getDatapoints();
            for (Object aDataPoint : dataPoint) {
                Datapoint dp = (Datapoint) aDataPoint;
                avgCPUUtilization = dp.getAverage();
            }

            return avgCPUUtilization;

        } catch (AmazonServiceException e) {
            error(e.getMessage(), e.getRawResponseContent());

        }
        return 0;
    }

    private Alert alert(Alert.AlertType alertType, String title, String header, String text) {
        final Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(text);
        alert.showAndWait();
        return alert;
    }

    private Alert error(String message, String text) {
        return alert(Alert.AlertType.ERROR, "Error!", message, text);
    }

    @FXML
    private void exit() {
        Platform.exit();
        System.exit(0);
    }

    @FXML
    private void about() {
        try {
            Desktop.getDesktop().browse(new URI("https://github.com/gadelkareem/aws-client"));
        } catch (Exception e) {
            error(e.getMessage(), stackTraceToString(e));

        }

    }


    @FXML
    private void directoryChooser() {
        try {
            DirectoryChooser directoryChooser = new DirectoryChooser();
            directoryChooser.setTitle("Choose Directory");
            File defaultDirectory = new File(!preferencesKeysPath.getText().isEmpty() ? preferencesKeysPath.getText() : getDefaultKeysPath());
            directoryChooser.setInitialDirectory(defaultDirectory);
            File selectedDirectory = directoryChooser.showDialog(null);
            if (selectedDirectory != null) {
                preferencesKeysPath.setText(selectedDirectory.getAbsolutePath());
            }
        } catch (Exception e) {
            error(e.getMessage(), stackTraceToString(e));

        }

    }

    private String getDefaultKeysPath() {
        return System.getProperty("user.home") + File.separator + ".ssh";
    }


    @FXML
    private void changeRegion() {
        try {
            userPreferences.put("aws.region", regionMenu.getValue().toString());
            initEc2View();
        } catch (Exception e) {
            error(e.getMessage(), stackTraceToString(e));

        }

    }

    private static String stackTraceToString(Exception e) {
        String result = e.toString() + "\n";
        StackTraceElement[] trace = e.getStackTrace();
        for (int i = 0; i < trace.length; i++) {
            result += trace[i].toString() + "\n";
        }
        return result;
    }
}


