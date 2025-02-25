package project2;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class Buy extends JFrame implements ActionListener {
    private String username;
    private JPanel artworkPanel;
    private JScrollPane scrollPane;
    private JComboBox<String> currencySelector;
    private Map<String, Double> exchangeRates; // Store exchange rates for currency conversion
    private Map<JLabel, Double> priceLabelsMap; // Store labels and their base prices

    // API Details
    private static final String API_KEY = "************************"; // Replace with your ExchangeRate-API key, You can get this by visiting the website for Exchange rate API
    private static final String API_URL = "https://v6.exchangerate-api.com/v6/" + API_KEY + "/latest/INR";

    public Buy(String username) {
        this.username = username;

        // Initialize exchange rates
        exchangeRates = new HashMap<>();
        exchangeRates.put("INR", 1.0); // Base currency
        exchangeRates.put("USD", 0.0);
        exchangeRates.put("Euro", 0.0);
        exchangeRates.put("Yen", 0.0);

        // Map to store price labels and their original prices
        priceLabelsMap = new HashMap<>();

        // Set up frame properties
        setTitle("Buy Art");
        setExtendedState(JFrame.MAXIMIZED_BOTH); // Full screen
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Load background image
        JLabel backgroundLabel = new JLabel(new ImageIcon("src/resources1/auction_bg.png"));
        setContentPane(backgroundLabel);
        backgroundLabel.setLayout(new BorderLayout());

        // Create currency selector
        String[] currencies = {"INR", "USD", "Euro", "Yen"};
        currencySelector = new JComboBox<>(currencies);
        currencySelector.setSelectedIndex(0);
        currencySelector.addActionListener(this); // Listen for currency changes
        JPanel currencyPanel = new JPanel();
        currencyPanel.setOpaque(false); // Make panel transparent
        currencyPanel.add(new JLabel("Select Currency: "));
        currencyPanel.add(currencySelector);
        backgroundLabel.add(currencyPanel, BorderLayout.NORTH); // Add to the top of the window

        // Create a panel to display artworks in a grid
        artworkPanel = new JPanel();
        artworkPanel.setLayout(new GridLayout(0, 3, 10, 10)); // 3 artworks per row
        artworkPanel.setOpaque(false); // Transparent panel

        // Scroll pane for artwork
        scrollPane = new JScrollPane(artworkPanel);
        scrollPane.setOpaque(false); // Transparent scroll pane
        scrollPane.getVerticalScrollBar().setUnitIncrement(20);
        scrollPane.getViewport().setOpaque(false); // Transparent viewport
        backgroundLabel.add(scrollPane, BorderLayout.CENTER);

        // Back button
        JButton backButton = new JButton("Back");
        backButton.setBackground(Color.RED);
        backButton.setForeground(Color.WHITE);
        backButton.setFont(new Font("Arial", Font.BOLD, 14));
        backButton.addActionListener(this);
        backgroundLabel.add(backButton, BorderLayout.SOUTH);

        // Fetch exchange rates in the background
        fetchExchangeRatesInBackground();

        // Start loading artwork in the background
        loadArtworkInBackground();

        setVisible(true);
    }

    // Fetch exchange rates using SwingWorker
    private void fetchExchangeRatesInBackground() {
        SwingWorker<Void, Void> rateWorker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                try {
                    URL url = new URL(API_URL);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.connect();

                    int responseCode = connection.getResponseCode();
                    if (responseCode != 200) {
                        throw new RuntimeException("HttpResponseCode: " + responseCode);
                    } else {
                        InputStreamReader in = new InputStreamReader(connection.getInputStream());
                        JsonObject jsonObject = JsonParser.parseReader(in).getAsJsonObject();

                        String result = jsonObject.get("result").getAsString();
                        if (!"success".equals(result)) {
                            throw new RuntimeException("API Error: " + jsonObject.get("error-type").getAsString());
                        }

                        JsonObject rates = jsonObject.getAsJsonObject("conversion_rates");
                        exchangeRates.put("USD", rates.get("USD").getAsDouble());
                        exchangeRates.put("Euro", rates.get("EUR").getAsDouble());
                        exchangeRates.put("Yen", rates.get("JPY").getAsDouble());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(Buy.this, "Failed to fetch exchange rates. Using default rates.", "Error", JOptionPane.ERROR_MESSAGE);
                }
                return null;
            }

            @Override
            protected void done() {
                // Optionally, you can notify the user that exchange rates have been updated
                System.out.println("Exchange rates updated.");
            }
        };
        rateWorker.execute();
    }

    // Load artwork data and images in the background using SwingWorker
    private void loadArtworkInBackground() {
        SwingWorker<Void, JPanel> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                // Load artwork data from the database
                try (Conn conn = new Conn()) {
                    Connection connection = conn.getConnection();
                    String query = "SELECT title, medium, price, image FROM artwork";
                    PreparedStatement pstmt = connection.prepareStatement(query);
                    ResultSet rs = pstmt.executeQuery();

                    // Loop through the result set and create components for each artwork
                    while (rs.next()) {
                        String title = rs.getString("title");
                        String medium = rs.getString("medium");
                        double price = rs.getDouble("price");
                        String imagePath = rs.getString("image");

                        // Create a panel for each artwork
                        JPanel artworkItem = createArtworkPanel(title, medium, price, imagePath);
                        publish(artworkItem); // Send the panel to be added to the UI
                    }
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
                return null;
            }

            @Override
            protected void process(java.util.List<JPanel> chunks) {
                // Add each artwork panel to the artworkPanel
                for (JPanel panel : chunks) {
                    artworkPanel.add(panel);
                }
                // Refresh the UI
                artworkPanel.revalidate();
                artworkPanel.repaint();
            }
        };

        // Execute the SwingWorker
        worker.execute();
    }

    // Create a panel for each artwork item
    private JPanel createArtworkPanel(String title, String medium, double price, String imagePath) {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2)); // Border around the artwork panel
        panel.setBackground(new Color(255, 255, 255, 200)); // Set a white background with some transparency

        // Add image
        ImageIcon artworkImage = new ImageIcon(imagePath); // Load the image from the path
        JLabel imageLabel = new JLabel(new ImageIcon(artworkImage.getImage().getScaledInstance(200, 200, Image.SCALE_SMOOTH)));
        panel.add(imageLabel, BorderLayout.WEST); // Add image to the left side

        // Create a box to hold the details on the right
        JPanel detailsPanel = new JPanel();
        detailsPanel.setLayout(new BoxLayout(detailsPanel, BoxLayout.Y_AXIS));
        detailsPanel.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1)); // Border around the details panel
        detailsPanel.setBackground(new Color(0, 0, 0, 150)); // Semi-transparent black background

        // Add title
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        titleLabel.setForeground(Color.WHITE); // Set text color to white
        detailsPanel.add(titleLabel);

        // Add medium
        JLabel mediumLabel = new JLabel("Medium: " + medium);
        mediumLabel.setFont(new Font("Arial", Font.BOLD, 14));
        mediumLabel.setForeground(Color.WHITE); // Set text color to white
        detailsPanel.add(mediumLabel);

        // Add price (this will be updated when currency changes)
        JLabel priceLabel = new JLabel("Price: Rs." + price);
        priceLabel.setFont(new Font("Arial", Font.BOLD, 14));
        priceLabel.setForeground(Color.WHITE); // Set text color to white
        detailsPanel.add(priceLabel);

        // Add price label and original price to map for dynamic updates
        priceLabelsMap.put(priceLabel, price);

        // Add Buy button
        JButton buyButton = new JButton("Buy: " + title);
        buyButton.setBackground(new Color(0, 102, 204));
        buyButton.setForeground(Color.WHITE);
        buyButton.setFont(new Font("Arial", Font.BOLD, 14));
        buyButton.setFocusPainted(false);
        buyButton.setBorderPainted(false);
        buyButton.setOpaque(true);
        buyButton.addActionListener(e -> {
            // Retain the Buy button functionality to redirect to the Payment class
            new Payment(this, title, username);
            this.setVisible(false);
        });
        detailsPanel.add(Box.createRigidArea(new Dimension(0, 10))); // Add spacing
        detailsPanel.add(buyButton);

        panel.add(detailsPanel, BorderLayout.CENTER); // Add details panel to the center

        return panel;
    }

    // Convert price based on selected currency
    private double convertPrice(double priceInINR, String selectedCurrency) {
        return priceInINR * exchangeRates.getOrDefault(selectedCurrency, 1.0);
    }

    // Update prices for all artwork items when currency changes
    private void updatePrices() {
        String selectedCurrency = (String) currencySelector.getSelectedItem();
        for (Map.Entry<JLabel, Double> entry : priceLabelsMap.entrySet()) {
            JLabel priceLabel = entry.getKey();
            double originalPrice = entry.getValue();
            double convertedPrice = convertPrice(originalPrice, selectedCurrency);
            String currencySymbol = getCurrencySymbol(selectedCurrency);
            priceLabel.setText("Price: " + currencySymbol + String.format("%.2f", convertedPrice));
        }
    }

    // Get currency symbol based on currency code
    private String getCurrencySymbol(String currencyCode) {
        switch (currencyCode) {
            case "USD":
                return "$";
            case "Euro":
                return "€";
            case "Yen":
                return "¥";
            case "INR":
            default:
                return "Rs.";
        }
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        if (ae.getSource() == currencySelector) {
            // Update prices when the currency is changed
            updatePrices();
        } else if (ae.getActionCommand().equals("Back")) {
            // Go back to the Home screen
            new Home(username).setVisible(true);
            this.setVisible(false); // Hide the Buy window
        }
    }

    public static void main(String[] args) {
        new Buy("testUser"); // Example username
    }
}
