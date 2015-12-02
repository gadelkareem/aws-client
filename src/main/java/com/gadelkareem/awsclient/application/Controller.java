package com.gadelkareem.awsclient.application;


import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Region;
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
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.util.Callback;

import java.util.ArrayList;
import java.util.List;


public class Controller {

    public TableView tableView;


    //INITIALIZE
    @FXML
    void initialize() {
        listEc2();
    }

    private void listEc2() {

        AWSCredentials awsCredentials = new DefaultAWSCredentialsProviderChain().getCredentials();
        AmazonEC2 amazonEC2 = new AmazonEC2Client(awsCredentials);
        amazonEC2.setRegion(Region.getRegion(Regions.EU_WEST_1));

        List<ObservableList<StringProperty>> rows = new ArrayList<ObservableList<StringProperty>>();
        List<String> columns = new ArrayList<String>();

        columns.add("Instance ID");
        columns.add("Group");
        columns.add("Instance Type");
        columns.add("Instance State");
        columns.add("Public IP");
        columns.add("Private IP");


        reservations:
        {
            for (Reservation reservation :
                    amazonEC2.describeInstances(new DescribeInstancesRequest()).getReservations()) {
                for (Instance instance : reservation.getInstances()) {

                    ObservableList<StringProperty> row = FXCollections.observableArrayList();

                    row.add(new SimpleStringProperty(instance.getInstanceId()));
                    row.add(new SimpleStringProperty(instance.getSecurityGroups().get(0).getGroupName()));
                    row.add(new SimpleStringProperty(instance.getInstanceType()));
                    row.add(new SimpleStringProperty(instance.getState().getName()));
                    row.add(new SimpleStringProperty(instance.getPublicIpAddress()));
                    row.add(new SimpleStringProperty(instance.getPrivateIpAddress()));

                    for (Tag tag : instance.getTags()) {
                        if (tag.getKey().equals("Name")) {
                            if (!columns.contains(tag.getKey()))
                                columns.add(0, tag.getKey());
                            row.add(0, new SimpleStringProperty(tag.getValue()));
                        } else {
                            if (!columns.contains("Tag::" + tag.getKey()))
                                columns.add("Tag::" + tag.getKey());
                            row.add(new SimpleStringProperty(tag.getValue()));
                        }
                    }
                    rows.add(row);
//                    break reservations;
                }
            }
        }


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


