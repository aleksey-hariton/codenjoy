package com.codenjoy.dojo.loderunner.model;

import com.codenjoy.dojo.loderunner.services.LoderunnerEvents;
import com.codenjoy.dojo.services.BoardReader;
import com.codenjoy.dojo.services.Dice;
import com.codenjoy.dojo.services.Point;
import com.codenjoy.dojo.services.Tickable;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static com.codenjoy.dojo.services.PointImpl.pt;

/**
 * User: sanja
 * Date: 17.12.13
 * Time: 4:56
 */
public class Loderunner implements Tickable, Field {

    private Point[][] field;
    private List<Player> players;
    private List<Enemy> enemies;
    private List<Gold> gold;

    private final int size;
    private Dice dice;

    public Loderunner(Level level, Dice dice) {
        this.dice = dice;
        size = level.getSize();
        field = new Point[size][size];

        toField(level.getBorders());
        toField(level.getBricks());
        toField(level.getLadder());
        toField(level.getPipe());

        gold = level.getGold();

        enemies = level.getEnemies();
        for (Enemy enemy : enemies) {
            enemy.init(this);
        }

        players = new LinkedList<Player>();
    }

    private void toField(List<? extends Point> elements) {
        for (Point element : elements) {
            field[element.getX()][element.getY()] = element;
        }
    }

    @Override
    public void tick() {
        Set<Player> die = new HashSet<Player>();

        heroesGo();
        die.addAll(getDied());

        enemiesGo();
        die.addAll(getDied());

        die.addAll(bricksGo());

        for (Player player : die) {
            player.event(LoderunnerEvents.KILL_HERO);
        }
    }

    private Set<Player> getDied() {
        Set<Player> die = new HashSet<Player>();

        for (Player player : players) {
            Hero hero = player.getHero();

            if (!hero.isAlive()) {
                die.add(player);
            }
        }

        return die;
    }

    public boolean is(Point pt, Class<? extends Point> elementType) {
        return is(pt.getX(), pt.getY(), elementType);
    }

    public boolean is(int x, int y, Class<? extends Point> elementType) {
        Point at = getAt(x, y);
        if (at == null) return false;
        return at.getClass().equals(elementType);
    }

    public BoardReader reader() {
        return new BoardReader() {

            private int size = Loderunner.this.size;
            private Point[][] field = Loderunner.this.field;

            @Override
            public int size() {
                return size;
            }

            @Override
            public Iterable<? extends Point> elements() {
                List<Point> result = new LinkedList<Point>();
                result.addAll(Loderunner.this.getHeroes());
                result.addAll(Loderunner.this.getEnemies());
                result.addAll(Loderunner.this.getGold());

                for (int x = 0; x < size; x++) {
                    for (int y = 0; y < size; y++) {
                        Point el = field[x][y];
                        if (el != null) {
                            result.add(el);
                        }
                    }
                }

                return result;
            }
        };
    }

    interface ElementsIterator {
        void it(Point element);
    }

    private void forAll(ElementsIterator iterator) {
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                iterator.it(field[x][y]);
            }
        }
    }

    private List<Player> bricksGo() {
        List<Player> die = new LinkedList<Player>();

        forAll(new ElementsIterator() {
            @Override
            public void it(Point element) {
                if (element instanceof Brick) {
                    ((Brick)element).tick();
                }
            }
        });

        for (Player player : players) {
            Hero hero = player.getHero();

            if (!hero.isAlive()) {
                Point element = getAt(hero);
                if (!(element instanceof Brick)) continue;

                // Умер от того что кто-то просверлил стенку
                die.add(player);

                Brick brick = (Brick)element;
                Hero killer = brick.getDrilledBy();
                Player killerPlayer = getPlayer(killer);
                if (killerPlayer != null && killerPlayer != player) {
                    killerPlayer.event(LoderunnerEvents.KILL_ENEMY);
                }
            }
        }

        return die;
    }

    public Point getAt(Point pt) {
        return getAt(pt.getX(), pt.getY());
    }

    public Point getAt(int x, int y) {
        if (x == -1 || y == -1) return null; // TODO это кажется только в тестах юзается, убрать бы отсюда для производительности
        return field[x][y];
    }

    private void heroesGo() {
        for (Player player : players) {
            Hero hero = player.getHero();

            hero.tick();

            if (gold.contains(hero)) {
                gold.remove(hero);
                player.event(LoderunnerEvents.GET_GOLD);

                Point pos = getFreeRandom();
                leaveGold(pos.getX(), pos.getY());
            }
        }
    }

    private void enemiesGo() {
        for (Enemy enemy : enemies) {
            enemy.tick();

            if (gold.contains(enemy) && !enemy.withGold()) {
                gold.remove(enemy);
                enemy.getGold();
            }
        }
    }

    private Player getPlayer(Hero hero) {
        for (Player player : players) {
            if (player.getHero() == hero) {
                return player;
            }
        }
        return null;
    }

    @Override
    public boolean isBarrier(int x, int y) {
        Point pt = pt(x, y);
        return x > size - 1 || x < 0 || y < 0 || y > size - 1 || isFullBrick(x, y) || is(pt, Border.class) || isHeroAt(x, y);
    }

    @Override
    public boolean tryToDrill(Hero byHero, int x, int y) {
        Point pt = pt(x, y);
        if (!isFullBrick(x, y)) {
            return false;
        }

        Point over = pt(x, y + 1);
        if (is(over, Ladder.class) || gold.contains(over) || isFullBrick(over.getX(), over.getY())
                || getHeroes().contains(over) || enemies.contains(over)) {
            return false;
        }

        Point el = getAt(pt);
        if (el instanceof Brick) {
            Brick brick = (Brick) el;
            brick.drill(byHero);
        }

        return true;
    }

    @Override
    public boolean isPit(int x, int y) {
        Point pt = pt(x, y - 1);

        if (!isFullBrick(pt.getX(), pt.getY()) && !is(pt, Ladder.class) && !is(pt, Border.class)
                && !getHeroes().contains(pt) && !enemies.contains(pt)) {
            return true;
        }

        return false;
    }

    @Override
    public boolean isFullBrick(int x, int y) {
        Point el = getAt(x, y);
        return el instanceof Brick && ((Brick)el).state(null) == Elements.BRICK;
    }

    @Override
    public Point getFreeRandom() {
        int rndX = 0;
        int rndY = 0;
        int c = 0;
        do {
            rndX = dice.next(size);
            rndY = dice.next(size);
        } while (!isFree(rndX, rndY) && c++ < 100);

        if (c >= 100) {
            return pt(0, 0);  // этого никогда не должно случиться, но никогда не говори никогда. чтобы заметить поставил координаты 0, 0
        }

        return pt(rndX, rndY);
    }

    @Override
    public boolean isLadder(int x, int y) {
        return is(x, y, Ladder.class);
    }

    @Override
    public boolean isPipe(int x, int y) {
        return is(x, y, Pipe.class);
    }

    @Override
    public boolean isFree(int x, int y) {
        Point pt = pt(x, y);

        return !gold.contains(pt) &&
                !is(pt, Border.class) &&
                !is(pt, Brick.class) &&
                !getHeroes().contains(pt) &&
                !is(pt, Pipe.class) &&
                !is(pt, Ladder.class);
    }

    @Override
    public boolean isHeroAt(int x, int y) {
        return getHeroes().contains(pt(x, y));
    }

    @Override
    public boolean isBrick(int x, int y) {
        return is(x, y, Brick.class);
    }

    @Override
    public boolean isEnemyAt(int x, int y) {
        return enemies.contains(pt(x, y));
    }

    @Override
    public void leaveGold(int x, int y) {
        gold.add(new Gold(x, y));
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isBorder(int x, int y) {
        return is(x, y, Border.class);
    }

    public List<Gold> getGold() {
        return gold;
    }

    @Override
    public List<Hero> getHeroes() {
        List<Hero> result = new LinkedList<Hero>();
        for (Player player : players) {
            result.add(player.getHero());
        }
        return result;
    }

    public void newGame(Player player) {
        if (!players.contains(player)) {
            players.add(player);
        }
        player.newHero(this);
    }

    public void remove(Player player) {
        players.remove(player);
    }

    public List<Enemy> getEnemies() {
        return enemies;
    }
}