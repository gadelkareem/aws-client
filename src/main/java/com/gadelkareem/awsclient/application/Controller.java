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
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;


public class Controller {

    private final ObservableList<InstanceView> rows = FXCollections.observableArrayList();
    public TableView tableView;
    public TableColumn id;
    public TableColumn name;

    //INITIALIZE
    @FXML
    void initialize() {
        listEc2();
    }

    private void listEc2() {

        AWSCredentials awsCredentials = new DefaultAWSCredentialsProviderChain().getCredentials();
        AmazonEC2 amazonEC2 = new AmazonEC2Client(awsCredentials);
        amazonEC2.setRegion(Region.getRegion(Regions.EU_WEST_1));

        id.setCellValueFactory(new PropertyValueFactory<InstanceView, String>("id"));
        name.setCellValueFactory(new PropertyValueFactory<InstanceView, String>("name"));
        reservations:
        {
            for (Reservation reservation :
                    amazonEC2.describeInstances(new DescribeInstancesRequest()).getReservations()) {
                for (Instance instance : reservation.getInstances()) {
                    rows.add(new InstanceView(instance.getInstanceId(), instance.getSecurityGroups().get(0).getGroupName()));

//                    break reservations;
                }
            }
        }

        tableView.setItems(FXCollections.observableList(rows));
    }
    //END OF INITIALIZE

    public class InstanceView {

        private SimpleStringProperty id;
        private SimpleStringProperty name;


        public InstanceView(String s1, String s2) {
            id = new SimpleStringProperty(s1);
            name = new SimpleStringProperty(s2);
        }

        public String getName() {
            return name.get();
        }


        public String getId() {
            return id.get();
        }


    }


}


