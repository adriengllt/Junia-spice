package com.jad;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.FileWriter;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {

    static final String DB_URL  = "jdbc:mariadb://jeanaymeric.hd.free.fr:8601/nfsproject";
    static final String DB_USER = "junia_isen2_2526";
    static final String DB_PASS = "yagoulou";

    static class Order {
        int numOrder;
        int idProduct;
        double quantity;

        Order(int numOrder, int idProduct, double quantity) {
            this.numOrder  = numOrder;
            this.idProduct = idProduct;
            this.quantity  = quantity;
        }
    }

    static class MachineToolPlan {
        int idMachineTool;
        List<Order> orders = new ArrayList<>();

        MachineToolPlan(int idMachineTool) {
            this.idMachineTool = idMachineTool;
        }

        void addOrder(int idProduct, double quantity) {
            orders.add(new Order(orders.size(), idProduct, quantity));
        }
    }

    static void buildPlan(Connection conn, int productId, double quantity,
                          List<MachineToolPlan> plans) throws SQLException {

        String sql = "SELECT r.id_machine_tool, r.id_ingredient, r.quantity_needed " +
                "FROM recipe r WHERE r.id_product = ?";

        int machineId = -1;
        List<Object[]> ingredients = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                machineId = rs.getInt("id_machine_tool");
                int    ingId  = rs.getInt("id_ingredient");
                double ingQty = rs.getDouble("quantity_needed") * quantity;
                ingredients.add(new Object[]{ingId, ingQty});
            }
        }

        if (machineId == -1) return;

        for (Object[] ing : ingredients) {
            int    ingId  = (int)    ing[0];
            double ingQty = (double) ing[1];
            if (hasRecipe(conn, ingId)) {
                buildPlan(conn, ingId, ingQty, plans);
            }
        }

        getOrCreate(plans, machineId).addOrder(productId, quantity);
    }

    static boolean hasRecipe(Connection conn, int productId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM recipe WHERE id_product = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    static MachineToolPlan getOrCreate(List<MachineToolPlan> plans, int machineId) {
        for (MachineToolPlan p : plans) {
            if (p.idMachineTool == machineId) return p;
        }
        MachineToolPlan p = new MachineToolPlan(machineId);
        plans.add(p);
        return p;
    }

    public static void main(String[] args) throws Exception {

        Scanner scanner = new Scanner(System.in);

        System.out.print("ID produit : ");
        int productId = Integer.parseInt(scanner.nextLine().trim());

        System.out.print("Quantite   : ");
        double quantity = Double.parseDouble(scanner.nextLine().trim());

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {

            List<MachineToolPlan> plans = new ArrayList<>();
            buildPlan(conn, productId, quantity, plans);

            if (plans.isEmpty()) {
                System.out.println("Aucun plan genere.");
                return;
            }

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String json = gson.toJson(plans);

            System.out.println(json);

            try (FileWriter fw = new FileWriter("output.json")) {
                fw.write(json);
            }
        }

        scanner.close();
    }
}