package study.querydsl.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

import study.querydsl.entity.Member;

/*
QuerydslPredicateExecutor<Member>의 한계점 -> 조인 불가능, 묵시적 조인은 가능.
클라이언트(서비스 클래스)가 QueryDsl에 의존해야함(QueryDsl 객체를 만들어서 넘김).
-> 문제점: QeuryDls기술이 다른걸로 대체되면 repository뿐만 아니라 의존된 코드 다 바꿔야함.
* */
@Repository
public interface MemberRepository extends JpaRepository<Member,Long>, MemberRepositoryCustom, QuerydslPredicateExecutor<Member> {

    List<Member> findByUsername(String username);
}
