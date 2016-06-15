package com.gadelkareem.awsclient.application;


import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
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
import com.amazonaws.services.ec2.model.*;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.DirectoryChooser;
import org.controlsfx.control.textfield.CustomTextField;
import org.controlsfx.control.textfield.TextFields;

import java.awt.*;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.prefs.Preferences;


public class Controller {


    public TableView<List<StringProperty>> tableView;
    public ChoiceBox<RegionChoice> regionMenu;
    public Menu launchShell;
    public MenuItem refreshTable;

    //Preferences form
    public GridPane preferencesForm;
    public TextField preferencesAccessKey;
    public TextField preferencesSecretKey;
    public CheckBox preferencesDisplayLoad;
    public TextField preferencesKeysPath;
    public TextField preferencesEc2User;
    public MenuItem copyCellValue;
    public MenuItem filterUsingCellValue;
    public TextField preferencesSshOptions;
    public HBox filterPlaceholder;

    private CustomTextField tableFilter;

    private ObservableSet<Integer> selectedRowIndexes = FXCollections.observableSet();
    private Preferences userPreferences = Preferences.userNodeForPackage(getClass());
    private String defaultRegion = Regions.EU_WEST_1.getName();
    private AWSCredentials awsCredentials;
    private AmazonEC2 amazonEC2Client;

    private SortedList<List<StringProperty>> sortedRows;
    private List<String> columns = new ArrayList<>();
    private List<RegionChoice> regionChoices = new ArrayList<>();

    //INITIALIZE
    @FXML
    void initialize() {
        initTableFilter();
        tableView.setOnMouseClicked(event -> {
            if (event.getButton().equals(MouseButton.PRIMARY)) {
                if (event.getClickCount() == 2) {
                    try {
                        final List<StringProperty> selectedRow = tableView.getSelectionModel().getSelectedItem();
                        execSshClient(selectedRow, selectedRow.get(columns.indexOf("Public DNS Name")).getValue());
                    } catch (Exception e) {
                        error(e.getMessage(), stackTraceToString(e));
                    }
                }
            }
        });

        if (!hasPreferences()) {
            try {
                awsCredentials = new DefaultAWSCredentialsProviderChain().getCredentials();
            } catch (Exception e) {
                awsCredentials = new BasicAWSCredentials("", "");
            }

            initPreferences();
            return;
        }
        initView();
    }

    private void initTableFilter() {
        tableFilter = (CustomTextField) TextFields.createClearableTextField();
        filterPlaceholder.getChildren().add(tableFilter);
        filterPlaceholder.setHgrow(tableFilter, Priority.ALWAYS);
        tableFilter.setVisible(false);
    }

    private void initView() {
        awsCredentials = new BasicAWSCredentials(userPreferences.get("aws.access_key", ""), userPreferences.get("aws.secret_key", ""));
        loadAmazonEC2Client();
        initRegionsMenu();
        initContextMenu();
    }

    private boolean hasPreferences() {
        return !(userPreferences.get("aws.access_key", "").isEmpty() && userPreferences.get("aws.secret_key", "").isEmpty());
    }

    @FXML
    private void initPreferences() {
        try {
            tableView.setVisible(false);
            tableFilter.setVisible(false);
            preferencesAccessKey.setText(userPreferences.get("aws.access_key", awsCredentials.getAWSAccessKeyId()));
            preferencesSecretKey.setText(userPreferences.get("aws.secret_key", awsCredentials.getAWSSecretKey()));
            preferencesEc2User.setText(userPreferences.get("aws.ec2_username", "ec2-user"));
            preferencesSshOptions.setText(userPreferences.get("aws.ssh_options", "-o CheckHostIP=no -o TCPKeepAlive=yes -o StrictHostKeyChecking=no -o ServerAliveInterval=120 -o ServerAliveCountMax=100"));
            preferencesKeysPath.setText(userPreferences.get("aws.keys_path", getDefaultKeysPath()));
            preferencesDisplayLoad.selectedProperty().setValue(userPreferences.getBoolean("view.column.load", false));
            preferencesForm.setVisible(true);
        } catch (Exception e) {
            error(e.getMessage(), stackTraceToString(e));
        }
    }

    @FXML
    private void savePreferences() {
        if (preferencesAccessKey.getText().isEmpty() || preferencesSecretKey.getText().isEmpty() || preferencesEc2User.getText().isEmpty() || preferencesKeysPath.getText().isEmpty()) {
            error("Missing Prefrences", "Please fill in all fields");
            return;
        }
        try {
            userPreferences.put("aws.access_key", preferencesAccessKey.getText());
            userPreferences.put("aws.secret_key", preferencesSecretKey.getText());
            userPreferences.put("aws.ec2_username", preferencesEc2User.getText());
            userPreferences.put("aws.ssh_options", preferencesSshOptions.getText());
            userPreferences.put("aws.keys_path", preferencesKeysPath.getText());
            userPreferences.putBoolean("view.column.load", preferencesDisplayLoad.isSelected());
            regionChoices.clear();
            preferencesForm.setVisible(false);
            initView();
        } catch (Exception e) {
            error(e.getMessage(), stackTraceToString(e));
        }
    }

    private void initContextMenu() {
        launchShell.setOnAction(event -> {
            try {
                final List<StringProperty> selectedRow = tableView.getSelectionModel().getSelectedItem();
                final MenuItem source = (MenuItem) event.getTarget();
                String ip = "";

                if (source.getText().equals("Public DNS Name")) {
                    ip = selectedRow.get(columns.indexOf("Public DNS Name")).getValue();
                } else if (source.getText().equals("Public IP")) {
                    ip = selectedRow.get(columns.indexOf("Public IP")).getValue();
                } else if (source.getText().equals("Private IP")) {
                    ip = selectedRow.get(columns.indexOf("Private IP")).getValue();
                }
                if (ip.isEmpty()) {
                    return;
                }
                execSshClient(selectedRow, ip);
            } catch (Exception e) {
                error(e.getMessage(), stackTraceToString(e));
            }


        });
        refreshTable.setOnAction(event -> initEc2View());
        copyCellValue.setOnAction(event -> {
            final ClipboardContent clipboardContent = new ClipboardContent();
            TablePosition position = tableView.getFocusModel().getFocusedCell();
            if (position == null || position.getColumn() < 0) {
                return;
            }
            TableColumn column = position.getTableColumn();
            int row = position.getRow();

            clipboardContent.putString(column.getCellData(row).toString());
            Clipboard.getSystemClipboard().setContent(clipboardContent);
        });
        filterUsingCellValue.setOnAction(event -> {
            TablePosition position = tableView.getFocusModel().getFocusedCell();
            if (position == null || position.getColumn() < 0) {
                return;
            }
            TableColumn column = position.getTableColumn();
            int row = position.getRow();
            tableFilter.setText(column.getCellData(row).toString());
        });
    }

    private void initRegionsMenu() {
        if (!regionChoices.isEmpty()) {
            return;
        }
        try {
            DescribeRegionsResult regionsResult = amazonEC2Client.describeRegions();
            List<Region> regions = regionsResult.getRegions();
            for (Region region : regions) {
                regionChoices.add(new RegionChoice(region, 0));

            }
            regionMenu.getSelectionModel().clearSelection();
            regionMenu.getItems().clear();
            regionMenu.getItems().setAll(regionChoices);
            regionMenu.getSelectionModel().select(getUserRegion());

            new Thread(() -> {
                try {
                    for (int i = 0; i < regionChoices.size(); i++) {
                        final RegionChoice regionChoice = regionChoices.get(i);
                        com.amazonaws.regions.Region generalRegion = RegionUtils.getRegion(regionChoice.getRegion().getRegionName());
                        if (generalRegion == null) {
                            Platform.runLater(() -> {
                                RegionChoice selectedRegionChoice = regionMenu.getSelectionModel().getSelectedItem();
                                regionMenu.getItems().remove(regionMenu.getItems().indexOf(regionChoice));
                                if (selectedRegionChoice == regionChoice) {
                                    regionMenu.getSelectionModel().select(getUserRegion());
                                }
                            });
                            regionChoices.remove(regionChoice);
                            i--;
                            continue;
                        }
                        amazonEC2Client.setRegion(generalRegion);
                        List<Reservation> reservations = amazonEC2Client.describeInstances().getReservations();
                        int numberOfInstances = 0;
                        if (!reservations.isEmpty()) {
                            for (Reservation reservation : reservations) {
                                numberOfInstances += reservation.getInstances().size();
                            }
                        }
                        regionChoice.setNumberOfInstances(numberOfInstances);
                        Platform.runLater(() -> {
                            RegionChoice selectedRegionChoice = regionMenu.getSelectionModel().getSelectedItem();
                            regionMenu.getItems().set(regionMenu.getItems().indexOf(regionChoice), regionChoice);
                            if (selectedRegionChoice == regionChoice) {
                                regionMenu.getSelectionModel().select(getUserRegion());
                            }
                        });
                    }

                } catch (Exception e) {
                    System.out.print(e);
                }
            }).start();
        } catch (Exception e) {
            error(e.getMessage(), stackTraceToString(e));
        }
    }

    private void initEc2View() {
        try {
            Region region = regionMenu.getSelectionModel().getSelectedItem().getRegion();
            AmazonCloudWatchClient cloudWatchClient = new AmazonCloudWatchClient(awsCredentials);
            amazonEC2Client.setRegion(RegionUtils.getRegion(region.getRegionName()));
            cloudWatchClient.setEndpoint(RegionUtils.getRegion(region.getRegionName()).getServiceEndpoint(ServiceAbbreviations.CloudWatch));

            ObservableList<List<StringProperty>> rows = FXCollections.observableArrayList();
            String firstColumnKey = "Name";

            columns.clear();

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
            columns.add("Launch Time");


            boolean hasFirstColumnKey = false;
            reservations:
            {
                for (Reservation reservation :
                        amazonEC2Client.describeInstances(new DescribeInstancesRequest()).getReservations()) {
                    for (Instance instance : reservation.getInstances()) {


                        List<StringProperty> row = new ArrayList<>();
                        row.add(new SimpleStringProperty(""));
                        row.add(new SimpleStringProperty(instance.getInstanceId()));
                        if (userPreferences.getBoolean("view.column.load", false)) {
                            row.add(new SimpleStringProperty(""));
                        }
                        row.add(new SimpleStringProperty(!instance.getSecurityGroups().isEmpty() ? instance.getSecurityGroups().get(0).getGroupName() : ""));
                        row.add(new SimpleStringProperty(instance.getInstanceType()));
                        row.add(new SimpleStringProperty(instance.getState().getName()));
                        row.add(new SimpleStringProperty(instance.getPublicDnsName()));
                        row.add(new SimpleStringProperty(instance.getPublicIpAddress()));
                        row.add(new SimpleStringProperty(instance.getPrivateIpAddress()));
                        row.add(new SimpleStringProperty(instance.getKeyName()));
                        row.add(new SimpleStringProperty(instance.getLaunchTime().toString()));

                        for (Tag tag : instance.getTags()) {
                            if (tag.getKey().equals(firstColumnKey) && !tag.getValue().isEmpty()) {
                                row.set(0, new SimpleStringProperty(tag.getValue()));
                                hasFirstColumnKey = true;
                            } else {
                                String columnHeader = "Tag::" + tag.getKey();
                                if (!columns.contains(columnHeader)) {
                                    columns.add(columnHeader);
                                }
                                if (columns.indexOf(columnHeader) >= row.size()) {
                                    for (int i = row.size(); i <= columns.indexOf(columnHeader); i++) {
                                        row.add(new SimpleStringProperty(""));
                                    }
                                }
                                row.set(columns.indexOf(columnHeader), new SimpleStringProperty(tag.getValue()));
                            }
                        }

                        rows.add(row);
                    }
                }
            }

            if (!hasFirstColumnKey) {
                columns.remove(0);
                for (List<StringProperty> row : rows) {
                    row.remove(0);
                }
            }


            tableView.getColumns().clear();

            for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
                tableView.getColumns().add(createColumn(columnIndex, columns.get(columnIndex)));
            }


            FilteredList<List<StringProperty>> filteredRows = new FilteredList<>(rows, p -> true);

            tableFilter.textProperty().addListener((observable, oldValue, newValue) -> {
                filteredRows.setPredicate(r -> {
                    String value = newValue.trim();
                    if (value.isEmpty()) {
                        return true;
                    }

                    String lowerCaseFilter = value.toLowerCase();

                    for (StringProperty cell : r) {
                        if (cell.getValue() == null || cell.getValue().isEmpty() || cell.getValue().equals("")) {
                            continue;
                        }
                        if (cell.getValue().toLowerCase().contains(lowerCaseFilter)) {
                            return true;
                        }
                    }
                    return false;
                });
            });
            sortedRows = new SortedList<>(filteredRows);
            sortedRows.comparatorProperty().bind(tableView.comparatorProperty());
            tableView.setItems(sortedRows);
            tableView.setColumnResizePolicy(p -> true);
            tableView.setVisible(true);
            tableFilter.clear();
            tableFilter.setVisible(true);
            if (userPreferences.getBoolean("view.column.load", false)) {
                final int columnsInstanceIdIndex = columns.indexOf("Instance ID");
                final int columnsInstanceLoadIndex = columns.indexOf("Instance Load");
                new Thread(() -> {
                    try {
                        for (List<StringProperty> row : sortedRows) {
                            String instanceId = row.get(columnsInstanceIdIndex).getValue();
                            if (!instanceId.isEmpty()) {
                                String instanceLoad = String.format("%.2g%n", getInstanceAverageLoad(cloudWatchClient, instanceId));
                                row.set(columnsInstanceLoadIndex, new SimpleStringProperty(instanceLoad));
                            }
                        }
                        Thread.sleep(1000);
                        Platform.runLater(() -> {
                            tableView.getColumns().get(columnsInstanceLoadIndex).setVisible(false);
                            tableView.getColumns().get(columnsInstanceLoadIndex).setVisible(true);
                        });
                    } catch (Exception e) {
                        System.out.print(e);
                    }
                }).start();
            }
        } catch (Exception e) {
            error(e.getMessage(), stackTraceToString(e));
            initPreferences();
        }

    }

    private TableColumn<List<StringProperty>, String> createColumn(
            final int columnIndex,
            String columnTitle
    ) {

        TableColumn<List<StringProperty>, String> column = new TableColumn<>();
        column.setText(columnTitle);

        column.setCellValueFactory(cellDataFeatures -> {
            List<StringProperty> row = cellDataFeatures.getValue();
            if (columnIndex >= row.size()) {
                return new SimpleStringProperty("");
            } else {
                return row.get(columnIndex);
            }

        });

        column.setCellFactory(column1 -> {
            TableCell<List<StringProperty>, String> cell = new TextFieldTableCell<>();
            cell.addEventFilter(MouseEvent.MOUSE_CLICKED, e ->
                    cell.setStyle("-fx-border-color:black black black black;-fx-background-color:#005BD1;-fx-text-fill:white")
            );
            cell.addEventFilter(MouseEvent.MOUSE_EXITED, e ->
                    cell.setStyle("")
            );
            return cell;

        });
        return column;
    }

    private double getInstanceAverageLoad(AmazonCloudWatchClient cloudWatchClient, String instanceId) {

        long offsetInMilliseconds = 1000 * 60 * 60;
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
            Desktop.getDesktop().browse(new URI("http://gadelkareem.com/aws-client"));
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
            if (regionMenu.getValue() == null) {
                return;
            }
            userPreferences.put("aws.region", regionMenu.getValue().getRegion().getRegionName());
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

    private class RegionChoice {
        private final Region region;
        private int numberOfInstances;

        public RegionChoice(Region region, int numberOfInstances) {
            this.region = region;
            this.numberOfInstances = numberOfInstances;
        }

        public Region getRegion() {
            return region;
        }

        public int getNumberOfInstances() {
            return numberOfInstances;
        }

        public void setNumberOfInstances(int numberOfInstances) {
            this.numberOfInstances = numberOfInstances;
        }

        public String toString() {
            return region.getRegionName() + "  (" + numberOfInstances + ")";
        }

    }

    private void loadAmazonEC2Client() {
        this.amazonEC2Client = new AmazonEC2Client(awsCredentials);
    }

    private RegionChoice getUserRegion() {
        String regionName = userPreferences.get("aws.region", defaultRegion);
        for (RegionChoice regionChoice : regionChoices) {
            if (regionChoice.getRegion().getRegionName().equals(regionName)) {
                return regionChoice;
            }
        }
        return regionChoices.get(0);

    }

    private void execSshClient(List<StringProperty> selectedRow, String ip) throws Exception {
        final int keyNameIndex = columns.indexOf("Key Name");
        final String keyName = selectedRow.get(keyNameIndex).getValue();

        final String os = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH);
        ProcessBuilder processBuilder;
        if (os.contains("mac") || os.contains("darwin")) {
            processBuilder = new ProcessBuilder("/usr/bin/osascript",
                    "-e", "tell app \"Terminal\"",
                    "-e", "set currentTab to do script " +
                    "(\"/usr/bin/ssh " + userPreferences.get("aws.ssh_options", "-o CheckHostIP=no -o TCPKeepAlive=yes -o StrictHostKeyChecking=no -o ServerAliveInterval=120 -o ServerAliveCountMax=100") + " -i " +
                    userPreferences.get("aws.keys_path", getDefaultKeysPath()) + File.separator + keyName + ".pem " +
                    userPreferences.get("aws.ec2_username", "ec2-user") + "@" +
                    ip + "\")",
                    "-e", "end tell");
        } else if (os.contains("win")) {
            processBuilder = new ProcessBuilder(System.getenv("ProgramFiles(X86)") + "\\PuTTy\\putty.exe\"", "-ssh", userPreferences.get("aws.ssh_options", "") + " -i " +
                    userPreferences.get("aws.keys_path", getDefaultKeysPath()) + File.separator + keyName + ".pem " +
                    userPreferences.get("aws.ec2_username", "ec2-user") + "@" +
                    ip);
//        } else if (os.contains("nux")) {
//            processBuilder = new ProcessBuilder("/usr/bin/ssh " + userPreferences.get("aws.ssh_options", "-o CheckHostIP=no -o TCPKeepAlive=yes -o StrictHostKeyChecking=no -o ServerAliveInterval=120 -o ServerAliveCountMax=100") + " -i " +
//                    userPreferences.get("aws.keys_path", getDefaultKeysPath()) + File.separator + keyName + ".pem " +
//                    userPreferences.get("aws.ec2_username", "ec2-user") + "@" +
//                    ip);
        } else {
            alert(Alert.AlertType.INFORMATION, "Info", "Unsupported operating system", "Launch shell is currently supported on Windows and OSX systems.");
            return;
        }

        processBuilder.start();

    }

}



