// SPDX-License-Identifier: GPL-3.0-only
using System;
using System.Collections.Generic;
using System.IO;
using System.Text;

namespace SpwIsland.Menu
{
    internal enum EntryKind { Title, Note, Separator, Toggle, Action }

    internal sealed class MenuEntry
    {
        internal EntryKind Kind;
        internal int Id;
        internal bool Selected;
        internal string Label;

        internal static List<MenuEntry> Read(TextReader input)
        {
            var entries = new List<MenuEntry>();
            string line;
            while ((line = input.ReadLine()) != null)
            {
                var parts = line.Split('\t');
                if (parts.Length != 4) throw new InvalidDataException("Invalid menu entry.");
                EntryKind kind;
                if (!Enum.TryParse(parts[0], true, out kind)) throw new InvalidDataException("Invalid menu entry kind.");
                entries.Add(new MenuEntry {
                    Kind = kind,
                    Id = Int32.Parse(parts[1]),
                    Selected = parts[2] == "1",
                    Label = Encoding.UTF8.GetString(Convert.FromBase64String(parts[3]))
                });
            }
            return entries;
        }
    }
}
