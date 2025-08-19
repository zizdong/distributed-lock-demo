package xyz.zizdong.provider.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;
import xyz.zizdong.provider.entity.Stock;

public interface StockMapper extends BaseMapper<Stock> {

    @Select("select * from tb_stock ts where ts.product_id = #{productId}")
    Stock selectByProductId(long productId);

    @Select("select * from tb_stock ts where ts.product_id = #{productId} for update")
    Stock selectByProductIdForUpdate(long productId);
}
