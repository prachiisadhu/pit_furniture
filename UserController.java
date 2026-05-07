// FILE: src/main/java/com/example/demo/controller/UserController.java
// REPLACE your existing UserController.java with this

package com.example.demo.controller;

import com.example.demo.entity.Order;
import com.example.demo.entity.Product;
import com.example.demo.entity.User;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.ProductRepository;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequestMapping("/user")
public class UserController {

    @Autowired private ProductRepository productRepo;
    @Autowired private OrderRepository orderRepo;

    private User getSessionUser(HttpSession session) {
        return (User) session.getAttribute("user");
    }

    // ────────────────────────────────────────────
    // SHOP
    // ────────────────────────────────────────────
    @GetMapping("/shop")
    public String shop(Model model, HttpSession session,
                       @RequestParam(required = false) String category,
                       @RequestParam(required = false) String search) {
        User user = getSessionUser(session);
        if (user == null) return "redirect:/login";

        List<Product> products = productRepo.findAll();
        if (category != null && !category.isEmpty())
            products = products.stream()
                    .filter(p -> p.getCategory().equalsIgnoreCase(category)).toList();
        if (search != null && !search.isEmpty()) {
            String kw = search.toLowerCase();
            products = products.stream()
                    .filter(p -> p.getName().toLowerCase().contains(kw) ||
                            (p.getDescription() != null && p.getDescription().toLowerCase().contains(kw)))
                    .toList();
        }
        model.addAttribute("products", products);
        model.addAttribute("user", user);
        model.addAttribute("selectedCategory", category);
        model.addAttribute("search", search);
        return "user/shop";
    }

    // ────────────────────────────────────────────
    // PRODUCT DETAIL
    // ────────────────────────────────────────────
    @GetMapping("/product/{id}")
    public String productDetail(@PathVariable int id, Model model, HttpSession session) {
        User user = getSessionUser(session);
        if (user == null) return "redirect:/login";
        model.addAttribute("product", productRepo.findById(id).orElse(null));
        model.addAttribute("user", user);
        return "user/product-detail";
    }

    // ────────────────────────────────────────────
    // CHECKOUT (NEW PAGE — between product-detail and place-order)
    // ────────────────────────────────────────────
    @GetMapping("/checkout/{id}")
    public String checkout(@PathVariable int id,
                           @RequestParam(defaultValue = "1") int quantity,
                           Model model, HttpSession session) {
        User user = getSessionUser(session);
        if (user == null) return "redirect:/login";
        Product product = productRepo.findById(id).orElse(null);
        if (product == null || product.getStock() < 1) return "redirect:/user/shop";
        // Clamp quantity to available stock
        if (quantity > product.getStock()) quantity = product.getStock();
        model.addAttribute("product", product);
        model.addAttribute("quantity", quantity);
        model.addAttribute("user", user);
        return "user/checkout";
    }

    // ────────────────────────────────────────────
    // PLACE ORDER (POST from checkout form)
    // ────────────────────────────────────────────
    @PostMapping("/place-order")
    public String placeOrder(@RequestParam int productId,
                             @RequestParam int quantity,
                             @RequestParam String phone,
                             @RequestParam String address,
                             @RequestParam String city,
                             @RequestParam String pincode,
                             HttpSession session) {
        User user = getSessionUser(session);
        if (user == null) return "redirect:/login";
        Product product = productRepo.findById(productId).orElse(null);
        if (product == null || product.getStock() < quantity) return "redirect:/user/shop";

        Order order = new Order();
        order.setOrderNumber("WC-" + System.currentTimeMillis());
        order.setCustomerName(user.getName());
        order.setCustomerEmail(user.getEmail());
        order.setCustomerPhone(phone);
        order.setShippingAddress(address);
        order.setCity(city);
        order.setPincode(pincode);
        order.setProductName(product.getName());
        order.setProductCategory(product.getCategory());
        order.setUnitPrice(product.getPrice());
        order.setQuantity(quantity);
        order.setTotalPrice(product.getPrice() * quantity);
        order.setStatus("CONFIRMED");
        order.setOrderDate(LocalDateTime.now());
        orderRepo.save(order);

        // Deduct stock
        product.setStock(product.getStock() - quantity);
        productRepo.save(product);

        return "redirect:/user/order-confirmation/" + order.getId();
    }

    // ────────────────────────────────────────────
    // ORDER CONFIRMATION
    // ────────────────────────────────────────────
    @GetMapping("/order-confirmation/{id}")
    public String orderConfirmation(@PathVariable int id, Model model, HttpSession session) {
        User user = getSessionUser(session);
        if (user == null) return "redirect:/login";
        model.addAttribute("order", orderRepo.findById(id).orElse(null));
        model.addAttribute("user", user);
        return "user/order-confirmation";
    }

    // ────────────────────────────────────────────
    // BILL / INVOICE
    // ────────────────────────────────────────────
    @GetMapping("/bill/{id}")
    public String bill(@PathVariable int id, Model model, HttpSession session) {
        User user = getSessionUser(session);
        if (user == null) return "redirect:/login";
        model.addAttribute("order", orderRepo.findById(id).orElse(null));
        model.addAttribute("user", user);
        return "user/bill";
    }

    // ────────────────────────────────────────────
    // MY ORDERS
    // ────────────────────────────────────────────
    @GetMapping("/my-orders")
    public String myOrders(Model model, HttpSession session) {
        User user = getSessionUser(session);
        if (user == null) return "redirect:/login";
        List<Order> orders = orderRepo.findByCustomerNameOrderByOrderDateDesc(user.getName());
        // totalSpent = sum of grandTotal (subtotal + GST) per order
        double totalSpent = orders.stream().mapToDouble(Order::getGrandTotal).sum();
        model.addAttribute("orders", orders);
        model.addAttribute("totalSpent", totalSpent);
        model.addAttribute("user", user);
        return "user/my-orders";
    }

    // ────────────────────────────────────────────
    // PROFILE
    // ────────────────────────────────────────────
    @GetMapping("/profile")
    public String profile(Model model, HttpSession session) {
        User user = getSessionUser(session);
        if (user == null) return "redirect:/login";
        List<Order> orders = orderRepo.findByCustomerNameOrderByOrderDateDesc(user.getName());
        model.addAttribute("orders", orders);
        model.addAttribute("user", user);
        return "user/profile";
    }
}