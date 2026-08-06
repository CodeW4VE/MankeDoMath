# MankeDoMath

A calculator inside Minecraft that knows what a shulker is.

Quick sums without leaving the game or reaching for your phone, aimed at what a
technical player actually works out: farm rates, how many shulkers a pile of
items is, how long something takes to fill.

**Server-side only.** Players install nothing. The server registers the command
and ships the tree to a vanilla client, which autocompletes it on its own.

```
/math 3456 items in sh      ->  2 shulkers
/math 5sh / 2h              ->  4320 items/hour (2.5 sh/hour)
/math (16*16*64) in st      ->  256 stacks, what one chunk section is worth
/math 20st in sh            ->  0.7407 shulkers
/math 1h + 30m              ->  108000 ticks (1h 30m)
```

Results are only visible to whoever asked. A calculator that shouts into public
chat is a calculator people turn off.

## Commands

| Command | What it does |
| --- | --- |
| `/w4ve math <expression>` | The canonical spelling |
| `/math <expression>` | Short alias, configurable, can be switched off |
| `/math help` | The examples above, clickable |
| `/math ans` | Your last result. Works inside expressions too: `/math ans / 64` |
| `/math share <expression>` | The one thing that does reach public chat |
| `/math reload` | Rereads the config (operator level 2) |

## Units

This is the part that makes it a Minecraft calculator rather than a calculator.

| Suffix | Is | Example |
| --- | --- | --- |
| `st` | stack, 64 | `3st` = 192 |
| `sh` | shulker, 27 stacks | `2sh` = 3456 |
| `dc` | double chest, 54 stacks | `1dc` = 3456 |
| `t` | ticks | `600t` = 30s |
| `s` `m` `h` | seconds, minutes, hours | `1h` = 72000t |
| `b` `c` `r` | blocks, chunks, regions | `2c` = 32 blocks |

Longer spellings work too (`stacks`, `shulkers`, `items`, `minutes`, `chunks`),
with or without a space after the number.

Convert with `in`:

```
/math 3456 items in sh
/math 100 blocks in c
/math 5sh in st
```

Units carry through the arithmetic, so the mod knows that `5sh / 2h` is a rate
and prints it per hour, and that `1sh + 1h` is a question with no answer.

**Operators**: `+ - * / % ^`, brackets, and a lone `x` between numbers as
multiplication. **Functions**: `sqrt min max abs floor ceil round log ln`.

## Safety

There is no `eval` here, and no scripting engine. The parser is hand written
and reads one small grammar and nothing else, because putting an interpreter on
a public server so people can add two numbers is opening the door to whoever
walks by.

Expression length, nesting depth and operation count are all checked before a
single multiplication happens, and the exponent cap is checked before each power
is computed, so `9^9^9` bounces with a message instead of eating a tick. All four
limits are in the config.

## Config

`config/mankedomath.conf`, plain `key=value` text, commented. Everything reloads
with `/math reload` except the command names, which are built into the command
tree at server start. The file says which is which.

```
short_alias=true
alias=math
max_length=256
max_depth=16
max_exponent=64
max_operations=500
decimals=4
click_to_copy=true
share_needs_permission=false
```

## Building

```
./gradlew build
```

Needs JDK 21. The jar lands in `build/libs/`. The `math` package is plain Java
with no Minecraft in it and has its own tests, which run in CI on every push:

```
./gradlew test
```

## License

MIT. Part of [W4VE](https://github.com/CodeW4VE), Ware 4 Vanilla Experience.
