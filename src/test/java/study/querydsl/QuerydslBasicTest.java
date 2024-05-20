package study.querydsl;

import static com.querydsl.jpa.JPAExpressions.select;
import static org.assertj.core.api.Assertions.assertThat;
import static study.querydsl.entity.QMember.member;
import static study.querydsl.entity.QTeam.team;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnit;
import study.querydsl.dto.MemberDto;
import study.querydsl.dto.QMemberDto;
import study.querydsl.dto.UserDto;
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

    //튜플이나 이러한 QueryDSL은 repository안에서만 알게 하고 DTO로 반환해서 사용하기

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
        //따로 작성하는게 좋다. <- 성능상 애매해질 수 있어서
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
    public void concat() {
        //{username}_{age}
        List<String> result = queryFactory
                .select(member.username.concat("_").concat(member.age.stringValue()))
                .from(member)
                .where(member.username.eq("member1"))
                .fetch();
        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    //프로젝션(select 대상 지정)과 결과 반환
    @Test
    public void simpleProjection() {
        List<String> result = queryFactory.select(member.username)
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    public void tupleProjection() {
        List<Tuple> result = queryFactory
                .select(member.username, member.age)
                .from(member)
                .fetch();

        for (Tuple tuple : result) {
            String username = tuple.get(member.username);
            Integer age = tuple.get(member.age);
            System.out.println("username = " + username);
            System.out.println("age = " + age);
        }

    }

    //순수 JPA에서 DTO조회할 때에는 new생성자를 사용함
    @Test
    public void findDtoByJQPL() {
        List<MemberDto> resultList = em.createQuery("select new study.querydsl.dto.MemberDto(m.username,m.age) from Member m", MemberDto.class)
                .getResultList();

        for (MemberDto memberDto : resultList) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    //Projections.bean은 getter setter를 통해 값을 받아옴 = @DATA 필수적
    @Test
    public void findDtoBySetter() {
        List<MemberDto> result = queryFactory
                .select(Projections.bean(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    //Projections.fields은 getter, setter없이 필드에 값이 들어감
    @Test
    public void findDtoByField() {
        List<MemberDto> result = queryFactory
                .select(Projections.fields(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    //Projections.constructor는 생성자를 통한 방식. 생성자 필가
    @Test
    public void findDtoByConstructor() {
        List<MemberDto> result = queryFactory
                .select(Projections.constructor(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    // 필드명이 다르면 as("원하는 필드명")를 사용하기. 아니면 null값이 들어간다
    @Test
    public void findUserDtoByField() {
        QMember memberSub = new QMember("memberSub");
        List<UserDto> result = queryFactory
                .select(Projections.fields(UserDto.class,
                        member.username.as("name"),
                        // member.age를 사용해도 되지만 서브쿼리를 만들어도 된다.
                        ExpressionUtils.as(JPAExpressions.select(memberSub.age.max())
                                .from(memberSub), "age")
                ))
                .from(member)
                .fetch();

        for (UserDto userDto : result) {
            System.out.println("memberDto = " + userDto);
        }
    }

    /*constructor와 비슷한 방식과의 차이
     장점: constructor는 런타임시 오류가 발생하고 QueryProjection은 컴파일 오류가 뜸
     단점: DTO가 QueryDSL에 의존성이 생김 -> 실용적으로 이런식으로 가져가도 되고 constructor방식을 사용해서 의존성없이 깔끔하게 가져갈 수 있다.
   */
    @Test
    public void findDtoByQueryProjection() {
        List<MemberDto> result = queryFactory
                .select(new QMemberDto(member.username, member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    // QueryDSL의 동적쿼리 사용
    @Test
    public void dynamicQuery_BooleanBuilder() {
        String usernameParam = "member1";
        Integer ageParam = 10;

        List<Member> result = searchMember1(usernameParam, ageParam);
        assertThat(result.size()).isEqualTo(1);
    }

    //BooleanBuilder를 사용하여 원하는 조건 추가하기
    private List<Member> searchMember1(String usernameCond, Integer ageCond) {

        BooleanBuilder builder = new BooleanBuilder();
        if (usernameCond != null) {
            builder.and(member.username.eq(usernameCond));
        }
        if (ageCond != null) {
            builder.and(member.age.eq(ageCond));
        }

        return queryFactory
                .selectFrom(member)
                .where(builder)
                .fetch();
    }

    @Test
    public void dynamicQuery_WhereParam() {
        String usernameParam = "member1";
        Integer ageParam = null;

        List<Member> result = searchMember2(usernameParam, ageParam);
        assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMember2(String usernameCond, Integer ageCond) {
        return queryFactory
                .selectFrom(member)
//                .where(usernameEq(usernameCond), ageEq(ageCond))
                .where(allEq(usernameCond,ageCond))
                .fetch();
    }

    private BooleanExpression usernameEq(final String usernameCond) {
        if (usernameCond == null) {
            return null;
        }
        return member.username.eq(usernameCond);
    }

    // 삼항연산자 사용
    private BooleanExpression ageEq(final Integer ageCond) {
        return ageCond != null ? member.age.eq(ageCond) : null;
    }

    //조합 가능 . 조합할때 조합하는 메서드는 predicate가 아니라 BooleanExpression이 자료형이여야함
    private Predicate allEq(final String usernameCond, final Integer ageCond) {
        return usernameEq(usernameCond).and(ageEq(ageCond));
    }

    //bulk처리 -> 한번에 여러 데이터를 수정,삭제함
    @Test
    @Commit
    public void bulkUpdate(){
        //전
        //member1 = 10 -> DB member1
        //member2 = 20 -> DB member2
        //member3 = 30 -> DB member3
        //member4 = 40 -> DB member4

        long count = queryFactory
                .update(member)
                .set(member.username, "비회원")
                .where(member.age.lt(28))
                .execute();
        em.flush();
        em.clear();
        //후
        //member1 = 10 -> DB 비회원
        //member2 = 20 -> DB 비회원
        //member3 = 30 -> DB member3
        //member4 = 40 -> DB member4

        // 벌크 연산 시 DB에 값을 바꾸고 영속성 컨텍스트는 바꾸지 않기 때문에 둘의 값이 다르다. em.flush, em.clear를 사용해서 영속성 컨텍스트를 제거함
        // * 영속성 컨텍스트와 DB의 값이 다르다면 DB의 값이 버려지고 영속성 컨텍스트의 값이 유지된다.*

        List<Member> result = queryFactory
                .selectFrom(member).fetch();

        for (Member member1 : result) {
            System.out.println("member1 = "+member1);
        }
    }

    @Test
    public void bulkAdd(){
        long count = queryFactory
                .update(member)
                .set(member.age, member.age.multiply(2))
                .execute();
        em.flush();
        em.clear();
        // em.flush, em.clear 안하면 값 변경안됨
        List<Member> result = queryFactory
                .selectFrom(member)
                .fetch();
        for (Member member1 : result) {
            System.out.println(member1.getAge());
        }
    }

    @Test
    public void bulkDelete(){
        long count = queryFactory
                .delete(member)
                .where(member.age.gt(18))
                .execute();
    }

    @Test
    public void sqlFunction(){
        List<String> result = queryFactory.select(Expressions.stringTemplate("function('replace',{0},{1},{2})",
                        member.username, "member", "M"))
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = "+s);
        }
    }

    @Test
    public void sqlFunction2(){
        List<String> result = queryFactory
                .select(member.username)
                .from(member)
//                .where(member.username.eq(Expressions.stringTemplate("function('lower',{0})",member.username)))
                .where(member.username.eq(member.username.lower()))
                .fetch();

        for (String s : result) {
            System.out.println("s = "+s);
        }
    }
}