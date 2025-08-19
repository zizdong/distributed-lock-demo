package xyz.zizdong.provider.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import xyz.zizdong.provider.entity.Stock;
import xyz.zizdong.provider.service.StockService;

import javax.annotation.Resource;

@Slf4j
@RestController
@RequestMapping("/provider/stock")
public class StockController {

    @Resource
    private StockService stockService;

    @PutMapping("/{productId}/sub/{count}")
    public String sub(@PathVariable("productId") long productId,
                      @PathVariable("count") int count) {
        try {
            Stock stock = stockService.subForRedis(productId, count);
            return String.format("商品：%d，数量:%d，扣减成功", productId, stock != null ? stock.getStock() : 0);
        } catch (Exception e) {
            log.error("商品：{}，数量:{}，扣减失败", productId, count);
            log.error("异常信息：", e);
            return String.format("商品：%d，数量:%d，扣减失败", productId, count);
        }
    }

    @PutMapping("/{productId}/add/{count}")
    public String add(@PathVariable("productId") long productId,
                      @PathVariable("count") int count) {
        try {
            Stock stock = stockService.add(productId, count);
            return String.format("商品：%d，数量:%d，增加成功", productId, stock.getStock());
        } catch (Exception e) {
            log.error("商品：{}，数量:{}，增加失败", productId, count);
            log.error("异常信息：", e);
            return String.format("商品：%d，数量:%d，增加失败", productId, count);
        }
    }

    @GetMapping("/{productId}")
    public String add(@PathVariable("productId") long productId) {
        Stock stock = stockService.get(productId);
        return String.format("商品：%d，数量:%d", productId, stock != null ? stock.getStock() : 0);
    }

}
