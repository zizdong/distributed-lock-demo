package xyz.zizdong.provider.service;

import xyz.zizdong.provider.entity.Stock;

public interface StockService {
    Stock sub(long productId, int count);

    Stock subForMySQL(long productId, int count);
    Stock subForRedis(long productId, int count);
    Stock subForWatchDog(long productId, int count);
    Stock subForZk(long productId, int count);


    Stock add(long productId, int count);

    Stock get(long productId);
}
