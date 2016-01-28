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
import com.amazonaws.services.ec2.model.InstanceStateName;
import com.amazonaws.services.ec2.model.InstanceType;
import com.amazonaws.services.ec2.model.Tag;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.DirectoryChooser;
import org.controlsfx.control.textfield.CustomTextField;
import org.controlsfx.control.textfield.TextFields;

import java.awt.*;
import java.io.File;
import java.net.URI;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;


public class Controller {


    public TableView tableView;
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
                        final List<StringProperty> selectedRow = ((List<StringProperty>) tableView.getSelectionModel().getSelectedItem());
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
                final List<StringProperty> selectedRow = ((List<StringProperty>) tableView.getSelectionModel().getSelectedItem());
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
            ObservableList<TablePosition> positionList = tableView.getSelectionModel().getSelectedCells();
            TablePosition position = positionList.get(0);
            TableColumn column = (TableColumn) tableView.getColumns().get(position.getColumn());
            int row = position.getRow();

            clipboardContent.putString(column.getCellData(row).toString());
            Clipboard.getSystemClipboard().setContent(clipboardContent);
        });
        filterUsingCellValue.setOnAction(event -> {
            ObservableList<TablePosition> positionList = tableView.getSelectionModel().getSelectedCells();
            TablePosition position = positionList.get(0);
            TableColumn column = (TableColumn) tableView.getColumns().get(position.getColumn());
            int row = position.getRow();
            tableFilter.setText(column.getCellData(row).toString());
        });
    }

    private void initRegionsMenu() {
        if (!regionChoices.isEmpty()) {
            return;
        }
//        try {
//            DescribeRegionsResult regionsResult = amazonEC2Client.describeRegions();
//            List<Region> regions = regionsResult.getRegions();
//            for (Region region : regions) {
//                com.amazonaws.regions.Region generalRegion = RegionUtils.getRegion(region.getRegionName());
//                if (generalRegion == null) {
//                    continue;
//                }
//                amazonEC2Client.setRegion(generalRegion);
//                List<Reservation> reservations = amazonEC2Client.describeInstances().getReservations();
//                int numberOfInstances = 0;
//                if (!reservations.isEmpty()) {
//                    for (Reservation reservation : reservations) {
//                        numberOfInstances += reservation.getInstances().size();
//                    }
//                }
//                regionChoices.add(new RegionChoice(region, numberOfInstances));
//
//            }
        for (Region generalRegion : RegionUtils.getRegions()) {
            regionChoices.add(new RegionChoice(generalRegion, randomInt(10, 60)));
        }
        regionMenu.getSelectionModel().clearSelection();
        regionMenu.getItems().clear();
        regionMenu.getItems().addAll(regionChoices);
        regionMenu.getSelectionModel().select(getUserRegion());
//        } catch (Exception e) {
//            error(e.getMessage(), stackTraceToString(e));
//        }
    }

    private void initEc2View() {
//        try {
        Region region = regionMenu.getSelectionModel().getSelectedItem().getRegion();
        AmazonCloudWatchClient cloudWatchClient = new AmazonCloudWatchClient(awsCredentials);
        amazonEC2Client.setRegion(RegionUtils.getRegion(region.getName()));
        cloudWatchClient.setEndpoint(RegionUtils.getRegion(region.getName()).getServiceEndpoint(ServiceAbbreviations.CloudWatch));

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


//            reservations:
//            {
//                for (Reservation reservation :
//                        amazonEC2Client.describeInstances(new DescribeInstancesRequest()).getReservations()) {
        for (int x = 0; x < regionMenu.getSelectionModel().getSelectedItem().getNumberOfInstances(); x++) {


            List<StringProperty> row = new ArrayList<>();
            row.add(new SimpleStringProperty(""));
            row.add(new SimpleStringProperty("i-" + randomInt(888888888, 99999999)));
            if (userPreferences.getBoolean("view.column.load", false)) {
//                    String instanceLoad = String.format("%.2g%n", getInstanceAverageLoad(cloudWatchClient, instance.getInstanceId()));
                row.add(new SimpleStringProperty("0." + randomInt(11, 99)));
            }
            String publicIp = randomIp();
            String privateIp = randomIp();
            row.add(new SimpleStringProperty(randomString(10)));
            row.add(new SimpleStringProperty(InstanceType.values()[randomInt(0, InstanceType.values().length - 1)].toString()));
            row.add(new SimpleStringProperty(InstanceStateName.values()[randomInt(0, InstanceStateName.values().length - 1)].toString()));
            row.add(new SimpleStringProperty("ec2-" + publicIp + "." + region.getName() + ".compute.amazonaws.com"));
            row.add(new SimpleStringProperty(publicIp));
            row.add(new SimpleStringProperty(privateIp));
            row.add(new SimpleStringProperty(randomString(8)));
            row.add(new SimpleStringProperty(new Date(Math.abs(System.currentTimeMillis() - randomInt(11111, 99999))).toString()));

            List<Tag> tags = new ArrayList<>(Arrays.asList(
                    new Tag("Name", randomString(8)),
                    new Tag("Group", randomString(8)),
                    new Tag("Env", randomString(8)),
                    new Tag("Key", randomString(8)),
                    new Tag(randomString(5), randomString(20))
            ));

            for (Tag tag : tags) {
                if (tag.getKey().equals(firstColumnKey) && !tag.getValue().isEmpty()) {
                    row.set(0, new SimpleStringProperty(tag.getValue()));
                    hasFirstColumnKey = true;
                } else {
                    String columnHeader = "Tag::" + tag.getKey();
                    if (!columns.contains(columnHeader))
                        columns.add(columnHeader);
                    if (columns.indexOf(columnHeader) >= row.size()) {
                        for (int i = row.size(); i <= columns.indexOf(columnHeader); i++) {
                            row.add(new SimpleStringProperty(""));
                        }
                    }
                    row.set(columns.indexOf(columnHeader), new SimpleStringProperty(tag.getKey()+"_"+tag.getValue()));
                }
            }

            rows.add(row);
        }
//                }
//            }

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
                if (newValue == null || newValue.isEmpty()) {
                    return true;
                }

                String lowerCaseFilter = newValue.toLowerCase();
                for (StringProperty cell : r) {
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
//        } catch (Exception e) {
//            error(e.getMessage(), stackTraceToString(e));
//            initPreferences();
//        }

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
        return column;
    }

    private double getInstanceAverageLoad(AmazonCloudWatchClient cloudWatchClient, String instanceId) {
        try {

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
//        try {
        if (regionMenu.getValue() == null) {
            return;
        }
        userPreferences.put("aws.region", regionMenu.getValue().getRegion().getName());
        initEc2View();
//        } catch (Exception e) {
//            error(e.getMessage(), stackTraceToString(e));
//
//        }

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
        private final int numberOfInstances;

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

        public String toString() {
            return region.getName() + "  (" + numberOfInstances + ")";
        }

    }

    private void loadAmazonEC2Client() {
        amazonEC2Client = new AmazonEC2Client(awsCredentials);
    }

    private RegionChoice getUserRegion() {
        String regionName = userPreferences.get("aws.region", defaultRegion);
        for (RegionChoice regionChoice : regionChoices) {
            if (regionChoice.getRegion().getName().equals(regionName)) {
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


    private String randomString(int len) {
        final String AB = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        Random rnd = new Random();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++)
            sb.append(AB.charAt(rnd.nextInt(AB.length())));
        return sb.toString();
    }

    private int randomInt(int min, int max) {
        return min + (int) (Math.random() * max);
    }

    private String randomIp() {
        return randomInt(11, 99) + "." + randomInt(11, 99) + "." + randomInt(11, 99) + "." + randomInt(11, 99);
    }
}


