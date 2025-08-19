package xyz.zizdong.provider.service.service;

import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import xyz.zizdong.provider.dao.StockMapper;
import xyz.zizdong.provider.entity.Stock;
import xyz.zizdong.provider.service.StockService;
import xyz.zizdong.provider.utils.DistributedLock4Redis;

import javax.annotation.Resource;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
public class StockServiceImpl implements StockService {

    @Resource
    private StockMapper stockMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private SqlSessionFactory sqlSessionFactory;

    @Resource
    private DistributedLock4Redis distributedLock4Redis;

    @Override
    public Stock sub(long productId, int count) {
        Stock stock = stockMapper.selectByProductId(productId);
        if (stock == null) {
            throw new RuntimeException("商品库存不足，没有初始化");
        }
        stock.setStock(stock.getStock() - count);
        if (stock.getStock() < 0) {
            throw new RuntimeException("商品库存不足");
        }
        stockMapper.updateById(stock);
        return stock;
    }

    @Override
    public Stock subForMySQL(long productId, int count) {
        SqlSession sqlSession = sqlSessionFactory.openSession(false);
        StockMapper manualCommitMapper = sqlSession.getMapper(StockMapper.class);
        Stock stock = manualCommitMapper.selectByProductIdForUpdate(productId);
        if (stock == null) {
            throw new RuntimeException("商品库存不足，没有初始化");
        }
        stock.setStock(stock.getStock() - count);
        if (stock.getStock() < 0) {
            throw new RuntimeException("商品库存不足");
        }
        manualCommitMapper.updateById(stock);
        sqlSession.commit();
        return stock;
    }

    @Override
    public Stock subForRedis(long productId, int count) {
        try {
            if (distributedLock4Redis.tryLock("PRODUCT:STOCK:LOCK:" + productId, 10, 10000)) {
                return sub(productId, count);
            }
            return null;
        } catch (Exception e) {
            log.error("扣减库存执行失败", e);
            return null;
        } finally {
            distributedLock4Redis.unlock("PRODUCT:STOCK:LOCK:" + productId);
        }
    }

    @Override
    public Stock subForWatchDog(long productId, int count) {
        return null;
    }

    @Override
    public Stock subForZk(long productId, int count) {
        return null;
    }

    @Override
    public Stock add(long productId, int count) {
        Stock stock = stockMapper.selectByProductId(productId);
        if (stock == null) {
            stock = Stock.builder()
                    .productId(productId)
                    .stock(count)
                    .build();
            stockMapper.insert(stock);
            return stock;
        }
        stock.setStock(stock.getStock() + count);
        stockMapper.updateById(stock);
        return stock;
    }

    @Override
    public Stock get(long productId) {
        String value = stringRedisTemplate.opsForValue().get("stock:" + productId);
        if (value == null) {
            Stock stock = stockMapper.selectByProductId(productId);
            stringRedisTemplate.opsForValue().set("stock:" + productId, JSONUtil.toJsonStr(stock));
            return stock;
        }
        return JSONUtil.toBean(value, Stock.class);
    }
}
