package xyz.zizdong.provider.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@TableName("tb_stock")
public class Stock {

    @TableId
    private Long id;

    private Long productId;

    private Integer stock;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;

}
