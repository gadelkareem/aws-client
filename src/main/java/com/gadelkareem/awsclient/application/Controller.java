package com.gadelkareem.awsclient.application;


import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.regions.Regions;
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
import java.util.List;


public class Controller {

    public TableView tableView;
    public ChoiceBox regionMenu;

    private Region defaultRegion = Region.getRegion(Regions.EU_WEST_1);
    AWSCredentials awsCredentials = new DefaultAWSCredentialsProviderChain().getCredentials();

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
        AmazonEC2 amazonEC2 = new AmazonEC2Client(awsCredentials);
        amazonEC2.setRegion((Region) regionMenu.getSelectionModel().getSelectedItem());

        List<ObservableList<StringProperty>> rows = new ArrayList<ObservableList<StringProperty>>();
        List<String> columns = new ArrayList<String>();

        String firstColumnKey = "Name";

        columns.add(firstColumnKey);
        columns.add("Instance ID");
        columns.add("Group");
        columns.add("Instance Type");
        columns.add("Instance State");
        columns.add("Public IP");
        columns.add("Private IP");


        boolean hasFirstColumnKey = false;
        int maxTagsCount = 0;
        reservations:
        {
            for (Reservation reservation :
                    amazonEC2.describeInstances(new DescribeInstancesRequest()).getReservations()) {
                for (Instance instance : reservation.getInstances()) {


                    ObservableList<StringProperty> row = FXCollections.observableArrayList();
                    row.add(new SimpleStringProperty(""));
                    row.add(new SimpleStringProperty(instance.getInstanceId()));
                    row.add(new SimpleStringProperty(instance.getSecurityGroups().get(0).getGroupName()));
                    row.add(new SimpleStringProperty(instance.getInstanceType()));
                    row.add(new SimpleStringProperty(instance.getState().getName()));
                    row.add(new SimpleStringProperty(instance.getPublicIpAddress()));
                    row.add(new SimpleStringProperty(instance.getPrivateIpAddress()));

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


}


