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


        addColumn(0, "Name");
        addColumn(1, "Group");
        addColumn(2, "Instance ID");
        addColumn(3, "Instance Type");
        addColumn(4, "Instance State");
        addColumn(5, "Public IP");
        addColumn(6, "Private IP");

        reservations:
        {
            for (Reservation reservation :
                    amazonEC2.describeInstances(new DescribeInstancesRequest()).getReservations()) {
                for (Instance instance : reservation.getInstances()) {

                    String instanceName = instance.getInstanceId();
                    for (Tag tag : instance.getTags()) {
                        if (tag.getKey().equals("Name")) {
                            instanceName = tag.getValue();
                            break;
                        }
                    }
                    String[] values = {
                            instanceName,
                            instance.getSecurityGroups().get(0).getGroupName(),
                            instance.getInstanceId(),
                            instance.getInstanceType(),
                            instance.getState().getName(),
                            instance.getPublicIpAddress(),
                            instance.getPrivateIpAddress()
                    };
                    addRow(values);
//                    break reservations;
                }
            }
        }

    }

    private void addRow(String[] values) {
        ObservableList<StringProperty> row = FXCollections.observableArrayList();
        for (String value : values) {
            row.add(new SimpleStringProperty(value));
        }
        tableView.getItems().add(row);
    }

    private void addColumn(final int columnIndex, String columnTitle) {
        
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
        tableView.getColumns().addAll(column);
    }


}


