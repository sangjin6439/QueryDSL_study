package study.querydsl;

import static org.assertj.core.api.Assertions.*;

import com.querydsl.jpa.impl.JPAQueryFactory;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import jakarta.persistence.EntityManager;
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
    public void before(){
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

        for(Member member : members){
            System.out.println("member = " + member);
            System.out.println("-> member.team = " + member.getTeam());
        }

    }

    @Test
    public void startJPQL(){
        //member1을 찾아라.
        Member findMember = em.createQuery("select m from Member m WHERE m.username = :username", Member.class)
                .setParameter("username", "member1")
                .getSingleResult();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test //장점: 런타임 시 자바 컴파일러의 도움을 받아 오류를 확인할 수 있다. 자동으로 파라미터를 바인딩해준다.
    public void startQuerydsl(){

        QMember m = new QMember("m");

        Member findMember = queryFactory
                .select(m)
                .from(m)
                .where(m.username.eq("member1")) // <-자동으로 바인딩을 해준다. sql인젝션 방어
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");

    }

}
