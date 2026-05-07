package com.example.demo.controller;

import com.example.demo.entity.Product;
import com.example.demo.entity.Order;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.OrderRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;

@Controller
@RequestMapping("/admin")
public class AdminController {

    @Autowired private ProductRepository repo;
    @Autowired private OrderRepository orderRepo;

    // ─────────────────────────────────────────────────────────────────────────
    // FIX: Save uploaded images to the EXTERNAL "uploads/images/products/"
    // directory (relative to the working directory where you run the app).
    //
    // In application.properties we added:
    //   spring.web.resources.static-locations=classpath:/static/,file:uploads/
    //
    // This means Spring Boot will serve anything inside uploads/ as a
    // static resource.  An image saved as:
    //   uploads/images/products/12345_chair.jpg
    // will be accessible at:
    //   http://localhost:8090/images/products/12345_chair.jpg
    //
    // The DB stores the URL path: /images/products/12345_chair.jpg
    // Thymeleaf renders: <img th:src="${p.image}"> → works perfectly.
    // ─────────────────────────────────────────────────────────────────────────
    private static final String UPLOAD_DIR = "uploads/images/products/";

    @PostConstruct
    public void init() {
        // Create the upload directory on startup if it doesn't exist
        File dir = new File(UPLOAD_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
            System.out.println("✅ Upload directory created: " + dir.getAbsolutePath());
        }
    }

    @GetMapping("")
    public String root() { return "redirect:/admin/dashboard"; }

    // ──────────────────────────────────────────
    // DASHBOARD
    // ──────────────────────────────────────────
    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        List<Product> products = repo.findAll();
        List<Order>   orders   = orderRepo.findAllByOrderByOrderDateDesc();
        double totalValue = 0;
        int    totalStock = 0;
        for (Product p : products) {
            totalValue += p.getPrice() * p.getStock();
            totalStock += p.getStock();
        }
        model.addAttribute("products",   products);
        model.addAttribute("orders",     orders);
        model.addAttribute("totalValue", totalValue);
        model.addAttribute("totalStock", totalStock);
        model.addAttribute("orderCount", orders.size());
        return "admin/dashboard";
    }

    // ──────────────────────────────────────────
    // PRODUCTS
    // ──────────────────────────────────────────
    @GetMapping("/products")
    public String products(Model model) {
        model.addAttribute("products", repo.findAll());
        return "admin/products";
    }

    @GetMapping("/add-product")
    public String showAddProductForm() { return "admin/add-product"; }

    @PostMapping("/add-product")
    public String saveProduct(@ModelAttribute Product product,
                              @RequestParam("imageFile") MultipartFile imageFile) {
        handleImageUpload(product, imageFile);
        repo.save(product);
        return "redirect:/admin/products";
    }

    @GetMapping("/delete/{id}")
    public String deleteProduct(@PathVariable int id) {
        repo.deleteById(id);
        return "redirect:/admin/products";
    }

    @GetMapping("/edit/{id}")
    public String editProduct(@PathVariable int id, Model model) {
        model.addAttribute("product", repo.findById(id).orElse(null));
        return "admin/edit-product";
    }

    @PostMapping("/update")
    public String updateProduct(@ModelAttribute Product product,
                                @RequestParam("imageFile") MultipartFile imageFile) {
        if (!imageFile.isEmpty()) {
            handleImageUpload(product, imageFile);
        }
        repo.save(product);
        return "redirect:/admin/products";
    }

    // ──────────────────────────────────────────
    // ORDERS
    // ──────────────────────────────────────────
    @GetMapping("/orders")
    public String orders(Model model) {
        model.addAttribute("orders", orderRepo.findAllByOrderByOrderDateDesc());
        return "admin/orders";
    }

    @PostMapping("/updateOrderStatus")
    public String updateOrderStatus(@RequestParam int orderId,
                                    @RequestParam String status) {
        orderRepo.findById(orderId).ifPresent(order -> {
            order.setStatus(status);
            orderRepo.save(order);
        });
        return "redirect:/admin/orders";
    }

    // ──────────────────────────────────────────
    // IMAGE UPLOAD HELPER
    // ──────────────────────────────────────────
    private void handleImageUpload(Product product, MultipartFile imageFile) {
        if (imageFile != null && !imageFile.isEmpty()) {
            try {
                File uploadDir = new File(UPLOAD_DIR);
                if (!uploadDir.exists()) uploadDir.mkdirs();

                String original = imageFile.getOriginalFilename();
                // Sanitise filename: remove spaces, special chars
                String safeName = (original != null)
                        ? original.replaceAll("[^a-zA-Z0-9._-]", "_")
                        : "image.jpg";
                String filename = System.currentTimeMillis() + "_" + safeName;

                // Save to external uploads/ folder
                Files.write(Paths.get(UPLOAD_DIR + filename), imageFile.getBytes());

                // Store URL path in DB — matches Spring Boot static serving URL
                product.setImage("/images/products/" + filename);

            } catch (IOException e) {
                System.err.println("❌ Image upload failed: " + e.getMessage());
            }
        }
    }
}