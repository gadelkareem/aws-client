package com.gadelkareem.awsclient.application;


import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
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
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.util.Callback;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;


public class Controller {


    public TableView tableView;
    public ChoiceBox regionMenu;

    private Region defaultRegion = Region.getRegion(Regions.EU_WEST_1);
    private AWSCredentials awsCredentials = new DefaultAWSCredentialsProviderChain().getCredentials();


    //INITIALIZE
    @FXML
    void initialize() {
        showRegionsMenu();
        showEc2s();
    }

    private void showRegionsMenu() {
        regionMenu.getItems().addAll(RegionUtils.getRegions());
        regionMenu.getSelectionModel().select(defaultRegion);
    }

    private void showEc2s() {
        Region region = (Region) regionMenu.getSelectionModel().getSelectedItem();
        AmazonEC2 amazonEC2Client = new AmazonEC2Client(awsCredentials);
        AmazonCloudWatchClient cloudWatchClient = new AmazonCloudWatchClient(awsCredentials);
        amazonEC2Client.setRegion(region);
        cloudWatchClient.setEndpoint(region.getServiceEndpoint(ServiceAbbreviations.CloudWatch));

        List<ObservableList<StringProperty>> rows = new ArrayList<ObservableList<StringProperty>>();
        List<String> columns = new ArrayList<String>();

        String firstColumnKey = "Name";

        columns.add(firstColumnKey);
        columns.add("Instance ID");
        columns.add("Instance Load");
        columns.add("Security Group");
        columns.add("Instance Type");
        columns.add("Instance State");
        columns.add("Public IP");
        columns.add("Private IP");
        columns.add("Key Name");


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
                    String instanceLoad = Double.toString(monitorInstance(cloudWatchClient, instance.getInstanceId().toString()));

                    row.add(new SimpleStringProperty(instanceLoad));
                    row.add(new SimpleStringProperty(instance.getSecurityGroups().get(0).getGroupName()));
                    row.add(new SimpleStringProperty(instance.getInstanceType()));
                    row.add(new SimpleStringProperty(instance.getState().getName()));
                    row.add(new SimpleStringProperty(instance.getPublicIpAddress()));
                    row.add(new SimpleStringProperty(instance.getPrivateIpAddress()));
                    row.add(new SimpleStringProperty(instance.getKeyName()));

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
//                    break reservations;
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

    public static double monitorInstance(AmazonCloudWatchClient cloudWatchClient, String instanceId) {
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

        } catch (AmazonServiceException ase) {
            System.out.println("Caught an AmazonServiceException, which means the request was made  "
                    + "to Amazon EC2, but was rejected with an error response for some reason.");
            System.out.println("Error Message:    " + ase.getMessage());
            System.out.println("HTTP Status Code: " + ase.getStatusCode());
            System.out.println("AWS Error Code:   " + ase.getErrorCode());
            System.out.println("Error Type:       " + ase.getErrorType());
            System.out.println("Request ID:       " + ase.getRequestId());

        }
        return 0;
    }
}


