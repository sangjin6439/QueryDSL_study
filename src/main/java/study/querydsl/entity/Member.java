package study.querydsl.entity;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
//편의를 위해 toString을 사용하면서 id, username, age만 출력하도록 설정. 주의할 점은 toString을 사용하면서 연관관계가 있는 필드는 사용하지 않는 것이 좋다. 무한루프 가능성 있음.
@ToString(of = {"id","username","age"})
public class Member {

    @Id @GeneratedValue
    @Column(name = "member_id")
    private Long id;
    private String username;
    private int age;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    public Member(String username){
        this(username, 0);
    }

    public Member(String username, int age){
        this(username, age, null);
    }

    public Member(String username, int age, Team team){
        this.username=username;
        this.age=age;
        if(team!=null){
            changeTeam(team);
        }
    }
    public void changeTeam(Team team){
        this.team=team;
        team.getMembers().add(this);
    }

}
