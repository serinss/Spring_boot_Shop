package com.shop.repository;

import com.querydsl.core.QueryResults;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.shop.constant.ItemSellStatus;
import com.shop.dto.ItemSearchDto;
import com.shop.dto.MainItemDto;
import com.shop.dto.QMainItemDto;
import com.shop.entity.Item;
import com.shop.entity.QItem;
import com.shop.entity.QItemImg;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.thymeleaf.util.StringUtils;

import javax.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;

// ItemRepositoryCustom 인터페이스를 구현하는 클래스 -> Impl을 붙여야 정상작동한다
public class ItemRepositoryCustomImpl implements ItemRepositoryCustom{

    private JPAQueryFactory queryFactory; // 동적 쿼리 작성

    public ItemRepositoryCustomImpl(EntityManager em){
        this.queryFactory = new JPAQueryFactory(em);
    }

    // 상품 판매 상태 조건이 전체(null)일 경우, null 리턴
    // null이 아니라 판매중 or 품절인 경우, 해당 조건의 상품만 리턴
    private BooleanExpression searchSellStatusEq(ItemSellStatus searchSellStatus){
        return searchSellStatus == null ? null : QItem.item.itemSellStatus.eq(searchSellStatus);
    }

    // searchDateType에 따라 dateTime의 값을 이전 시간의 값으로 세팅 후, 해당 시간 이후로 등록된 상품만 조회
    private BooleanExpression regDtsAfter(String searchDataType){
        LocalDateTime dateTime = LocalDateTime.now();

        if(StringUtils.equals("all", searchDataType) || searchDataType == null){
            return null;
        } else if(StringUtils.equals("1d", searchDataType)){
            dateTime = dateTime.minusDays(1);
        } else if(StringUtils.equals("1w", searchDataType)){
            dateTime = dateTime.minusWeeks(1);
        } else if(StringUtils.equals("1m", searchDataType)){
            dateTime = dateTime.minusMonths(1);
        } else if(StringUtils.equals("6m", searchDataType)){
            dateTime = dateTime.minusMonths(6);
        }

        return QItem.item.regTime.after(dateTime);
    }

    // searchBy의 값에 따라 상품명에 검색어를 포함하고 있는 상품만 조회
    private BooleanExpression searchByLike(String searchBy, String searchQuery){

        if(StringUtils.equals("itemNm", searchBy)){
            return QItem.item.itemNm.like("%" + searchQuery + "%");
        } else if(StringUtils.equals("createdBy", searchBy)){
            return QItem.item.createdBy.like("%" + searchQuery + "%");
        }

        return null;
    }

    // 네이티브 쿼리 작성
    @Override
    public Page<Item> getAdminItemPage(ItemSearchDto itemSearchDto, Pageable pageable){
        QueryResults<Item> results = queryFactory
                .selectFrom(QItem.item) // 상품 데이터를 조회하기 위해 QItem의 item을 지정
                .where(regDtsAfter(itemSearchDto.getSearchDateType()), searchSellStatusEq(itemSearchDto.getSearchSellStatus()),
                        searchByLike(itemSearchDto.getSearchBy(), itemSearchDto.getSearchQuery())) //조건
                .orderBy(QItem.item.id.desc())
                .offset(pageable.getOffset()) // 데이터를 가지고 올 시작 인텍스 지정
                .limit(pageable.getPageSize()) // 한 번에 가지고 올 최대 개수 지정
                .fetchResults(); // 조회한 리스트 및 전체 개수를 포함하는 QueryResults 반환

        List<Item> content = results.getResults();
        long total = results.getTotal();
        return new PageImpl<>(content, pageable, total);
        // 조회한 데이터를 Page클래스의 구현체인 PageImpl 객체로 반환
    }

    //메인 페이지 출력---------------------------------------------------------------------------------------------------
    private BooleanExpression itemNmLike(String searchQuery){
        return StringUtils.isEmpty(searchQuery)?null:QItem.item.itemNm.like("%"+searchQuery+"%");
    }

    @Override
    public Page<MainItemDto> getMainItemPage(ItemSearchDto itemSearchDto, Pageable pageable){
        QItem item = QItem.item;
        QItemImg itemImg = QItemImg.itemImg;

        QueryResults<MainItemDto> results = queryFactory
                .select(
                        new QMainItemDto(
                                item.id,
                                item.itemNm,
                                item.itemDetail,
                                itemImg.imgUrl,
                                item.price)
                )
                .from(itemImg)
                .join(itemImg.item, item)
                .where(itemImg.repimgYn.eq("Y"))
                .where(itemNmLike(itemSearchDto.getSearchQuery()))
                .orderBy(item.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetchResults();

        List<MainItemDto> content = results.getResults();
        long total = results.getTotal();
        return new PageImpl<>(content, pageable, total);
    }
}
