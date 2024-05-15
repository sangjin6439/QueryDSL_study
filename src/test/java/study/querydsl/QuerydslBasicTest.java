package study.querydsl;

import static com.querydsl.jpa.JPAExpressions.select;
import static org.assertj.core.api.Assertions.assertThat;
import static study.querydsl.entity.QMember.member;
import static study.querydsl.entity.QTeam.team;

import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnit;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;
import study.querydsl.entity.Team;

@SpringBootTest
@Transactional
public class QuerydslBasicTest {

    @Autowired
    EntityManager em;
    //따로 빼서 사용하는 걸 권장
    JPAQueryFactory queryFactory;

    @BeforeEach
    public void before() {
        //초기화
        queryFactory = new JPAQueryFactory(em);

        Team teamA = new Team("teamA");
        Team teamB = new Team("teamB");
        em.persist(teamA);
        em.persist(teamB);

        Member member1 = new Member("member1", 10, teamA);
        Member member2 = new Member("member2", 20, teamA);

        Member member3 = new Member("member3", 30, teamB);
        Member member4 = new Member("member4", 40, teamB);

        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(member4);

        //초기화
        em.flush();
        em.clear();

        //확인
        List<Member> members = em.createQuery("select m from Member m", Member.class)
                .getResultList();

        for (Member member : members) {
            System.out.println("member = " + member);
            System.out.println("-> member.team = " + member.getTeam());
        }
    }

    @Test
    public void startJPQL() {
        //member1을 찾아라.
        Member findMember = em.createQuery("select m from Member m WHERE m.username = :username", Member.class)
                .setParameter("username", "member1")
                .getSingleResult();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test //장점: 런타임 시 자바 컴파일러의 도움을 받아 오류를 확인할 수 있다. 자동으로 파라미터를 바인딩해준다.
    public void startQuerydsl() {
//        여러 테이블을 조인할 때 이렇게 따로따로 Q타입 만들기
//        QMember m1 = new QMember("m1");

        Member findMember = queryFactory
                //QMemeber.memeber를 static import로 변경했다.
                .select(member)
                .from(member)
                .where(member.username.eq("member1")) // <-자동으로 바인딩을 해준다. sql인젝션 방어
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");

    }

    @Test
    public void search() {
        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1")
                        .and(member.age.eq(10)))
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @DisplayName("검색 조건 쿼리")
    @Test
    public void searchAndParam() {
        Member findMember = queryFactory
                .selectFrom(member) //and연산은 ,을 이용해서 where에 파라미터를 넘기면 다 조립이 된다.
                .where(member.username.eq("member1"), (member.age.eq(10)))
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @DisplayName("결과 조회")
    @Test
    public void resultFetch() {

        //List
        List<Member> fetch = queryFactory
                .selectFrom(member)
                .fetch();

    /* 단 건 <- 현재 test에서 @beforeEach로 memeber1,2,3,4가 테스트 전에 수행되고 persist가 되기에
       조회하는 member가 4개가 돼서 결과가 둘 이상이라 NonUniqueResultException 예외 발생
    */
        Member findMember1 = queryFactory
                .selectFrom(member)
                .fetchOne();

        //처음 한 건 조회
        Member findMember2 = queryFactory
                .selectFrom(member)
                .fetchFirst();

        // 페이징에서 사용(따라서 fetch는 실행된 쿼리의 결과를 가져오는 데 사용되는 메서드이고, ResultFetch는 결과를 처리하고 다양한 방식으로 조작할 수 있는 타입) <-deprecated
        QueryResults<Member> results = queryFactory
                .selectFrom(member)
                .fetchResults();

        //count 쿼리로 변경
        long count = queryFactory
                .selectFrom(member)
                .fetchCount();
    }

    /*
     * 1.회원 정렬 순서(desc)
     * 2.회원 이름 올림차순(asc)
     * 단 2에서 회원 이름이 없으면 마지막에 출력(nulls last)
     * */

    @Test
    public void sort() {
        em.persist(new Member(null, 100));
        em.persist(new Member("member5", 100));
        em.persist(new Member("member6", 100));

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(100))
                .orderBy(member.age.desc(), member.username.asc().nullsLast())
                .fetch();

        Member member5 = result.get(0);
        Member member6 = result.get(1);
        Member memberNull = result.get(2);
        assertThat(member5.getUsername()).isEqualTo("member5");
        assertThat(member6.getUsername()).isEqualTo("member6");
        assertThat(memberNull.getUsername()).isNull();
    }

    @Test
    public void paging() {
        List<Member> result = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1)
                .limit(2)
                .fetch();

        assertThat(result.size()).isEqualTo(2);
    }

    @Test
    public void paging2() {
        //페이징 쿼리가 단순하면 이렇게 짜면되지만, 컨텐츠 쿼리가 복잡한데 카운트 쿼리는 단순하게 짤 수 있으면
        //따로 작성하는게 좋다. <- 성능상 애매해질 수 있어서 따로 작성
        QueryResults<Member> queryResults = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1)
                .limit(2)
                .fetchResults();

        assertThat(queryResults.getTotal()).isEqualTo(4);
        assertThat(queryResults.getLimit()).isEqualTo(2);
        assertThat(queryResults.getOffset()).isEqualTo(1);
        assertThat(queryResults.getResults().size()).isEqualTo(2);
    }

    //정렬
    @Test
    public void aggregation() {
        //QueryDsl이 제공하는 Tuple자료형. 여러 개의 타입이 있을 때 꺼내올 수 있음 <- 이 방법보다는 DTO로 뽑아오는 방법을 실무에서 사용함.
        List<Tuple> result = queryFactory
                .select(
                        member.count()
                        , member.age.sum()
                        , member.age.avg()
                        , member.age.max()
                        , member.age.min()
                )
                .from(member)
                .fetch();

        Tuple tuple = result.get(0);
        assertThat(tuple.get(member.count())).isEqualTo(4);
        assertThat(tuple.get(member.age.sum())).isEqualTo(100);
        assertThat(tuple.get(member.age.avg())).isEqualTo(25);
        assertThat(tuple.get(member.age.max())).isEqualTo(40);
        assertThat(tuple.get(member.age.min())).isEqualTo(10);
    }

    /*
     * 팀의 이름과 각 팀의 평균 연령을 구해라.
     * */
    @Test
    public void group() throws Exception {
        List<Tuple> result = queryFactory
                .select(team.name, member.age.avg())
                .from(member)
                .join(member.team, team)
                .groupBy(team.name)
                .fetch();

        Tuple teamA = result.get(0);
        Tuple teamB = result.get(1);

        assertThat(teamA.get(team.name)).isEqualTo("teamA");
        assertThat(teamA.get(member.age.avg())).isEqualTo(15); //(10 +20) / 2

        assertThat(teamB.get(team.name)).isEqualTo("teamB");
        assertThat(teamB.get(member.age.avg())).isEqualTo(35); //(30 +40) / 2
    }

    /*
     * 팀 A에 소속된 모든 회원
     * */
    @Test
    public void join() {
        List<Member> result = queryFactory
                .selectFrom(member)
                .join(member.team, team)
                .where(team.name.eq("teamA"))
                .fetch();

        assertThat(result)
                .extracting("username")
                .containsExactly("member1", "member2");
    }

    /*
     * 세타 조인 -> 연관관계가 없는 테이블끼리 조인
     * 회원의 이름이 팀 이름과 같은 회원 조회
     * */
    @Test
    public void theta_join() {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        em.persist(new Member("teamC"));

        List<Member> result = queryFactory
                .select(member)
                .from(member, team)
                .where(member.username.eq(team.name))
                .fetch();

        assertThat(result)
                .extracting("username")
                .containsExactly("teamA", "teamB");
    }

    /*
     * 예) 회원과 팀을 조인하면서, 팀 이름이 teamA인 팀만 조인, 회원은 모두 조회
     * JPQL: select m, t from Member m left join m.team t on t.name = 'teamA'
     * */
    @Test
    public void join_on_filtering() {
        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                //내부 조인(join = innerJoin)이면 익숙한 where절로 해결하고, 외부 조인이면 on절로 해결
                .leftJoin(member.team, team).on(team.name.eq("teamA"))
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    /*
     * 연관관계 없는 엔티티 외부 조인
     * 회원의 이름이 팀 이름과 같은 대상 외부 조인
     * */
    @Test
    public void join_on_no_filtering() {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        em.persist(new Member("teamC"));

        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                //이 부분이 일반 조인과 다르게 엔티티가 하나만 들어감
                .leftJoin(team).on(member.username.eq(team.name))
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    @PersistenceUnit
    EntityManagerFactory emf;

    @Test
    public void fetchJoin() {
        em.flush();
        em.clear();

        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1"))
                .fetchOne();

        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("페치 조인 미적용").isFalse();

    }

    @Test
    public void fetchJoinUse() {
        em.flush();
        em.clear();

        Member findMember = queryFactory
                .selectFrom(member)
                .join(member.team, team).fetchJoin()
                .where(member.username.eq("member1"))
                .fetchOne();

        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("페치 조인 적용").isTrue();
    }

    /*
     * 나이가 가장 많은 회원 조회
     * */
    @Test
    //JPAExpreesions를 스태틱 임포트로 만들어서 가능한거임.
    public void subQuery() {
        //엘리어스가 겹치면 안되기 때문에 따로 만들어 줌.
        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(
                        //이 부분 JPAExpreesions
                        select(memberSub.age.max())
                                .from(memberSub)
                ))
                .fetch();

        assertThat(result).extracting("age")
                .containsExactly(40);

    }

    /*
     * 나이가 평균 이상인 회원
     * */
    @Test
    public void subQueryGoe() {
        //엘리어스가 겹치면 안되기 때문에 따로 만들어 줌.
        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                //goe는 SQL에서 "Greater Than or Equal"을 나타내는 비교 연산자. 이 연산자는 주어진 값보다 크거나 같은 값을 가지는지 확인. In도 사용가능
                .where(member.age.goe(
                        select(memberSub.age.avg())
                                .from(memberSub)
                ))
                .fetch();

        assertThat(result).extracting("age")
                .containsExactly(30, 40);

    }

    @Test
    public void selectSubQuery() {
        QMember memberSub = new QMember("memberSub");

        List<Tuple> result = queryFactory
                .select(member.username, select(memberSub.age.avg())
                        .from(memberSub))
                .from(member)
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    /*
     * from 절의 서브 쿼리 한계
     * JPA JQPL 서브쿼리의 한계점으로 from절의 서브 쿼리(인라인 뷰)는 지원하지 않는다.
     *
     * 해결 방안
     * 서브쿼리를 join으로 변경한다.(가능한 상황이 많다. 불가능할 수도 있음.)
     * 애플리케이션에서 쿼리를 2번 분리해서 실행한다.
     * 최후로는 nativeSQL을 사용한다.
     *
     * DB는 데이터만 필터링하고 그루핑하는거고, 로직들은 애플리케이션에서 프레젠테이션로직은 프레젠테이션에서만 해결해야한다. DB는 데이터를 퍼올리는 역할만 집중!
     * 한방 쿼리의 함정! 너무 긴 쿼리 한번보다 나누어서 여러 쿼리로 만드는게 더 좋을 수 있다.
     *  */

    // 이런 부분은 DB보다는 서비스 로직에서 진행하는게 좋다고 함. DB에서는 조회만.. 어쩔 때는 DB에서 하는게 나을 수도 있음
    @Test
    public void basicCase() {
        List<String> result = queryFactory
                .select(member.age
                        .when(10).then("열상")
                        .when(20).then("스무살")
                        .otherwise("기타"))
                .from(member)
                .fetch();
        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    public void complexCase() {
        List<String> result = queryFactory
                .select(new CaseBuilder()
                        .when(member.age.between(0, 20)).then("0~20살")
                        .when(member.age.between(21, 30)).then("20~30살")
                        .otherwise("기타"))
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s= " + s);
        }
    }

    @Test
    public void constant() {
        List<Tuple> result = queryFactory
                .select(member.username, Expressions.constant("A"))
                .from(member)
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    /*concat은 문자열을 처리할 때 사용되지만 member.age.stringValue())
      age와 같은 숫자나 ENUM타입은 .stringValue()를 사용하여 처리 가능하다.
     */
    @Test
    public void concat(){
        //{username}_{age}
        List<String> result = queryFactory
                .select(member.username.concat("_").concat(member.age.stringValue()))
                .from(member)
                .where(member.username.eq("member1"))
                .fetch();
        for (String s : result) {
            System.out.println("s = "+ s);
        }
    }

}